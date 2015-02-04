package simplehttpclient;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Iterator;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.sun.xml.internal.messaging.saaj.util.ByteInputStream;

public class SimpleParseWorker implements Runnable {

	private Logger logger = LogManager.getLogger(SimpleParseWorker.class);

	private BlockingQueue<SimpleResponse> needParseQueue;

	private Queue<String> outputQueue;

	private Queue<SimpleKeyWords> kwQueue;

	private boolean running = false;

	public SimpleParseWorker(BlockingQueue<SimpleResponse> needParseQueue,
			Queue<String> outputQueue, Queue<SimpleKeyWords> kwQueue) {
		this.needParseQueue = needParseQueue;
		this.outputQueue = outputQueue;
		this.kwQueue = kwQueue;
	}

	public void stop() {
		running = false;
	}

	@Override
	public void run() {
		running = true;
		while (running) {
			byte[] content;
			try {
				SimpleResponse res = needParseQueue.poll(10, TimeUnit.SECONDS);
				if (res == null) {
					continue;
				}
				content = res.getContent();
				if (logger.isDebugEnabled()) {
					logger.debug(Thread.currentThread().getName() + " parsing res:" + res.getReq().getHost());
				}
				Document doc = Jsoup.parse(new ByteInputStream(content,
						content.length), res.getCharset(), res.getReq()
						.getHost());
				Elements links = doc.select("a");
				SimpleKeyWords kw = new SimpleKeyWords();

				// URL url = new URL(spec)
				String host = res.getReq().getHost();
				String path = res.getReq().getPath();
				kw.setUrl(host + path);
				Elements titles = doc.select("title");
				if (titles.size() > 0) {
					kw.setTitle(titles.first().text());
				}
				kwQueue.offer(kw);

				Iterator<Element> it = links.listIterator();
				while (it.hasNext()) {
					Element link = it.next();
					String href = link.attr("href");
					if (href != null && !href.startsWith("javascript:")
							&& !href.toLowerCase().startsWith("mailto:")) {
						// ¾Ý¶ÔÂ·¾¶
						if (href.startsWith("http://")) {
							outputQueue.offer(href);
						} else {
							URL newURL = new URL("http",host ,path);
							outputQueue.offer(new URL(newURL,href).toString());
						}
					}
				}
			} catch (IOException e) {
				logger.error("parse error:", e);
			} catch (InterruptedException e) {
				e.printStackTrace();
				if (logger.isDebugEnabled()) {
					logger.debug("killed");
				}
				running = false;
			} catch(Exception e){
				logger.error("unkown error:", e);
			}
		}
	}

	public static void main(String[] args) throws MalformedURLException, URISyntaxException {
		URL newURL = new URL("http://www.163.com/2345/234");
		System.out.println(new URL(newURL,"shit"));
		System.out.println(7 >> 8);
	}
}
