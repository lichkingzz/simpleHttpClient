package simplehttpclient;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SimpleHttpClient {

	private final static Logger logger = LogManager
			.getLogger(SimpleHttpClient.class);

	private Selector selector;

	private AtomicBoolean started = new AtomicBoolean(false);

	private boolean flag = false;

	private CountDownLatch latch = new CountDownLatch(1);

	public SimpleHttpClient() throws IOException {
		selector = Selector.open();
	}

	public SimpleFutrue execute(String host, String path,
			SimpleCallback callback) throws IOException, InterruptedException {
		if (started.compareAndSet(false, true)) {
			flag = true;
			start();
		}
		SocketChannel channel = SocketChannel.open();
		channel.configureBlocking(false);
		if (logger.isDebugEnabled()) {
			logger.debug("发送缓冲区大小："
					+ channel.getOption(StandardSocketOptions.SO_SNDBUF));
			logger.debug("接收缓冲区大小："
					+ channel.getOption(StandardSocketOptions.SO_RCVBUF));
		}

		SimpleGet get = new SimpleGet(host, path);
		SimpleFutrue result = new SimpleFutrue(get, this, callback);
		selector.wakeup();
		// 此方法会被select堵塞，所以先wakeup再执行…………
		SelectionKey key = channel.register(selector, SelectionKey.OP_CONNECT,
				result);
		result.setKey(key);
		key.attach(result);
		channel.connect(new InetSocketAddress(host, 80));
		// 唤醒!线程未启动就触发wakeup怎么办。。。
		latch.await();
		selector.wakeup();

		if (channel.finishConnect()) {
			channel.register(selector, SelectionKey.OP_WRITE);
		}

		return result;
	}

	public void stop() {
		flag = false;
		selector.wakeup();
	}

	private void start() {
		new Thread(new Runnable() {

			@Override
			public void run() {
				latch.countDown();
				while (flag) {
					try {
						selector.select(1000);
						Set<SelectionKey> keys = selector.selectedKeys();
						if (keys.size() == 0) {
							continue;
						}
						Iterator<SelectionKey> it = keys.iterator();
						while (it.hasNext()) {
							SelectionKey key = it.next();
							SimpleFutrue futrue = (SimpleFutrue) key
									.attachment();
							SimpleGet get = futrue.getReq();
							SimpleResponse res = futrue.getRes();
							ByteBuffer readBuffer = get.getReadBuffer();
							ByteBuffer writeBuffer = get.getWriteBuffer();
							if (key.isConnectable()) {
								SocketChannel sc = (SocketChannel) key
										.channel();
								if (sc.isConnectionPending()) {
									sc.finishConnect();
								}
								// 连接后立刻注册监听写事件
								key.interestOps(SelectionKey.OP_WRITE);
							} else if (key.isReadable()) {
								SocketChannel sChannel = (SocketChannel) key
										.channel();
								readBuffer.clear();
								int size;
								while ((size = sChannel.read(readBuffer)) > 0) {
									readBuffer.flip();
									res.write(readBuffer);
									readBuffer.clear();
								}
								if (size < 0 || res.isCompleted()) {
									// 读取完就关闭之！
									sChannel.close();
									futrue.finish();
								}
							} else if (key.isWritable()) {
								SocketChannel sChannel = (SocketChannel) key
										.channel();
								writeBuffer.clear();
								writeBuffer.put(get.getReq().getBytes());
								writeBuffer.flip();
								sChannel.write(writeBuffer);
								key.interestOps(SelectionKey.OP_READ);
							}
							it.remove();
						}
					} catch (Exception e) {
						logger.error("network exception", e);
					}
				}
			}

		}).start();
	}

	public static void main(String[] args) throws UnknownHostException,
			IOException, InterruptedException, ExecutionException {
		SimpleHttpClient hc = new SimpleHttpClient();
		String[] hosts = new String[] {"http://www.youku.com","http://tv.sohu.com/","http://v.qq.com/","http://www.iqiyi.com/" };
		LinkedBlockingQueue<String> urlQueue = new LinkedBlockingQueue<String>();
		urlQueue.addAll(Arrays.asList(hosts));
		final LinkedBlockingQueue<SimpleResponse> parseQueue = new LinkedBlockingQueue<SimpleResponse>(
				20);
		final LinkedBlockingQueue<SimpleKeyWords> resultQueue = new LinkedBlockingQueue<SimpleKeyWords>();
		ExecutorService exService = Executors.newCachedThreadPool();

		for (int j = 0; j < 3; j++) {
			SimpleParseWorker worker = new SimpleParseWorker(parseQueue,
					urlQueue, resultQueue);
			exService.execute(worker);
		}

		new Thread(new Runnable() {
			@Override
			public void run() {
				SimpleKeyWords kw;
				try {
					int i = 0;
					File urltitle = new File("h:/urltitle.txt");
					FileOutputStream out = new FileOutputStream(urltitle);
					BufferedWriter writer = new BufferedWriter(
							new OutputStreamWriter(out));
					while (i < 100) {
						kw = resultQueue.poll(10, TimeUnit.SECONDS);
						if (kw == null) {
							continue;
						}
						writer.append(kw.getUrl() + '\t' + kw.getTitle()
								+ "\r\n");
						i++;
						if (i % 100 == 0) {
							writer.flush();
						}
					}
					writer.close();
				} catch (InterruptedException e) {
					logger.error("poll keyword eror:", e);
				} catch (FileNotFoundException e) {
					logger.error("file not found", e);
				} catch (IOException e) {
					logger.error("io error", e);
				}
			}
		}).start();

		int i = 0;
		while (i < 500) {
			String urlStr = urlQueue.poll(10, TimeUnit.SECONDS);
			if (urlStr == null) {
				continue;
			}
			i++;
			URL next = new URL(urlStr);
			String path = next.getPath();
			if ("".equals(path)) {
				path = "/";
			}
			hc.execute(next.getHost(), path, new SimpleCallback() {
				@Override
				public void getResponse(SimpleGet get, SimpleResponse res) {
					parseQueue.offer(res);
				}
			});
			if (i % 10 == 0) {
				Thread.sleep(5000);
			}
		}

		System.out.println("shutting down!");
		hc.stop();
		List<Runnable> runners = exService.shutdownNow();
		for (Runnable runner : runners) {
			SimpleParseWorker worker = (SimpleParseWorker) runner;
			worker.stop();
		}
	}

}
