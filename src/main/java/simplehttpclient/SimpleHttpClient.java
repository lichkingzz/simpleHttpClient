package simplehttpclient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
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
						selector.select();
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
						e.printStackTrace();
					}
				}
			}

		}).start();
	}

	public static void main(String[] args) throws UnknownHostException,
			IOException, InterruptedException, ExecutionException {
		SimpleHttpClient hc = new SimpleHttpClient();
		String[] hosts = new String[] { "www.baidu.com", "www.zhihu.com",
				"www.sina.com.cn", "nba.hupu.com" };
		SimpleFutrue[] futrues = new SimpleFutrue[hosts.length];
		int i = 0;
		final LinkedBlockingQueue<SimpleResponse> parseQueue = new LinkedBlockingQueue<SimpleResponse>(
				20);

		ExecutorService exService = Executors.newCachedThreadPool();

		for (int j = 0; j < 3; j++) {
			SimpleParseWorker worker = new SimpleParseWorker(parseQueue, null);
			exService.execute(worker);
		}

		for (String string : hosts) {
			SimpleFutrue result = hc.execute(string, "/", new SimpleCallback() {
				@Override
				public void getResponse(SimpleGet get, SimpleResponse res) {
					System.out.println(get.getHost());
					parseQueue.offer(res);
					System.out.println();
				}
			});
			futrues[i] = result;
			i++;
		}
		for (int j = 0; j < futrues.length; j++) {
			futrues[j].get();
		}
		hc.stop();
		System.out.println("shutting down!");
		List<Runnable> runners = exService.shutdownNow();
		for (Runnable runner : runners) {
			SimpleParseWorker worker = (SimpleParseWorker) runner;
			worker.stop();
		}
	}
}
