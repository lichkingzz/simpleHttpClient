package simplehttpclient;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class SimpleFutrue implements Future<SimpleResponse> {

	private SelectionKey key = null;

	private SimpleGet req = null;

	private SimpleHttpClient hc = null;

	private SimpleResponse res = new SimpleResponse();

	private boolean canceled = false;

	private boolean isDone = false;

	private CountDownLatch latch = new CountDownLatch(1);

	private SimpleCallback callback;

	public SimpleFutrue(SimpleGet req, SimpleHttpClient hc,
			SimpleCallback callback) {
		this.hc = hc;
		this.req = req;
		this.callback = callback;
		this.res.setReq(req);
	}

	@Override
	public boolean cancel(boolean mayInterruptIfRunning) {
		if (isCancelled()) {
			return true;
		}
		try {
			key.cancel();
			key.channel().close();
			canceled = true;
			return true;
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
	}

	@Override
	public boolean isCancelled() {
		return canceled;
	}

	@Override
	public boolean isDone() {
		return isDone;
	}

	@Override
	public SimpleResponse get() throws InterruptedException, ExecutionException {
		latch.await();
		return res;
	}

	@Override
	public SimpleResponse get(long timeout, TimeUnit unit)
			throws InterruptedException, ExecutionException, TimeoutException {
		latch.await(timeout, unit);
		return res;
	}

	public void finish() {
		latch.countDown();
		isDone = true;
		if (callback != null) {
			callback.getResponse(req, res);
		}
	}

	public SimpleGet getReq() {
		return req;
	}

	public SimpleHttpClient getHc() {
		return hc;
	}

	public SimpleResponse getRes() {
		return res;
	}

	public void setKey(SelectionKey key) {
		this.key = key;
	}

}
