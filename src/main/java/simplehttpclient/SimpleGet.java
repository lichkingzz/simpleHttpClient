package simplehttpclient;

import java.nio.ByteBuffer;

public class SimpleGet {

	private String host;

	private String path;

	private ByteBuffer writeBuffer = ByteBuffer.allocate(1024);

	private ByteBuffer readBuffer = ByteBuffer.allocate(1024);

	public SimpleGet(String host, String path) {
		this.host = host;
		this.path = path;
	}

	public String getReq() {
		String get = "GET " + path + " HTTP/1.1\r\n";
		String headers = "Host: "
				+ host
				+ "\r\n"
				+ "Accept: text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8\r\n"
				+ "Accept-Encoding: gzip, deflate\r\n"
				+ "Accept-Language: zh-CN,zh;q=0.8,en;q=0.6\r\n";
		String blank = "\r\n";
		return get + headers + blank;
	}

	@Override
	public int hashCode() {
		return getReq().hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == null) {
			return false;
		}
		if (obj instanceof SimpleGet) {
			return getReq().equals(((SimpleGet) obj).getReq());
		}
		return false;
	}

	public ByteBuffer getWriteBuffer() {
		return writeBuffer;
	}

	public ByteBuffer getReadBuffer() {
		return readBuffer;
	}

	public String getHost() {
		return host;
	}

	public String getPath() {
		return path;
	}

}
