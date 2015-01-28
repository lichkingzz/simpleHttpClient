package simplehttpclient;

import java.io.IOException;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import com.sun.xml.internal.messaging.saaj.util.ByteInputStream;

public class SimpleParseWorker implements Runnable {

	private Logger logger = LogManager.getLogger(SimpleParseWorker.class);
	
	private BlockingQueue<SimpleResponse> needParseQueue;

	private Queue<String> outputQueue;

	private boolean running = false;

	public SimpleParseWorker(BlockingQueue<SimpleResponse> needParseQueue,
			Queue<String> outputQueue) {
		this.needParseQueue = needParseQueue;
		this.outputQueue = outputQueue;
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
				if(logger.isDebugEnabled()){
					logger.debug("parsing res:" + res.getReq().getHost());
				}					
				Document doc = Jsoup.parse(new ByteInputStream(content,
						content.length), res.getCharset(), res.getReq()
						.getHost());
				// Elements links = doc.select("a");
				// Iterator<Element> it = links.listIterator();
				// while (it.hasNext()) {
				// Element link = it.next();
				// }
			} catch (IOException e) {
				logger.error("parse error:" , e);
			} catch (InterruptedException e) {
				e.printStackTrace();
				if(logger.isDebugEnabled()){
					logger.debug("killed");
				}					
				running = false;
			}
		}
	}
}
