package simplehttpclient;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SimpleResponse {

	private static final Logger logger = LogManager
			.getLogger(SimpleResponse.class);

	private Map<String, String> headers = new HashMap<String, String>(10);

	private ByteArrayOutputStream content = new ByteArrayOutputStream(1024);

	private String statuLine = "";

	private long readCount = 0l;

	private long total = Long.MAX_VALUE;

	private boolean isChunk = false;

	private boolean completed = false;

	private boolean readingStatus = true;

	private boolean readingHeader = true;

	private boolean readingChunkSize = true;

	private boolean unzip = false;
	
	private SimpleGet req = null;
	

	public SimpleGet getReq() {
		return req;
	}

	public void setReq(SimpleGet req) {
		this.req = req;
	}

	private void addHeader(String key, String value) {
		key = key.toLowerCase();
		if ("content-length".equals(key)) {
			total = Long.parseLong(value);
		} else if ("transfer-encoding".equals(key)
				&& "chunked".equalsIgnoreCase(value)) {
			isChunk = true;
		}
		headers.put(key, value);
	}

	public String getStatusLine() {
		return statuLine.trim();
	}

	public Map<String, String> getHeaders() {
		return Collections.unmodifiableMap(headers);
	}

	public String getHeader(String key) {
		return headers.get(key.toLowerCase());
	}

	public byte[] getContent() throws IOException {
		if ("gzip".equals(getHeader("content-encoding")) && !unzip) {
			GZIPInputStream gin = new GZIPInputStream(new ByteArrayInputStream(
					content.toByteArray()));
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			byte[] buffer = new byte[256];
			int n;
			while ((n = gin.read(buffer)) >= 0) {
				out.write(buffer, 0, n);
			}
			unzip = true;
			content = out;
		}
		return content.toByteArray();
	}

	private StringBuffer tempHeader = new StringBuffer(2000);

	private boolean meetCR = false;
	private int changeLine = 0;

	private void writeHeader(ByteBuffer buffer) throws IOException {

		while (buffer.hasRemaining()) {
			char c = (char) buffer.get();
			if (readingStatus) {
				statuLine += c;
			} else {
				tempHeader.append(c);
			}
			if (c == '\r') {
				meetCR = true;
			} else if (meetCR && c == '\n') {
				changeLine++;
				readingStatus = false;
			} else {
				meetCR = false;
				changeLine = 0;
			}
			if (changeLine > 1) {
				parseHeader(tempHeader);
				readingHeader = false;
				tempHeader = null;
				break;
			}
		}
		if (!readingHeader && buffer.hasRemaining()) {
			writeContent(buffer);
		}
	}

	private void parseHeader(StringBuffer headerString) throws IOException {
		BufferedReader reader = new BufferedReader(new StringReader(
				headerString.toString()));
		String line = null;
		while ((line = reader.readLine()) != null) {
			int index = line.indexOf(':');
			if (index == -1) {
				continue;
			}
			String key = line.substring(0, index);
			String value = line.substring(index + 1, line.length());
			addHeader(key.toLowerCase().trim(), value.trim());
		}
	}

	public void write(ByteBuffer buffer) throws IOException {
		if (readingHeader) {
			writeHeader(buffer);
		} else {
			writeContent(buffer);
		}
	}

	private boolean firstChunk = true;

	private void writeContent(ByteBuffer buffer) throws IOException {
		if (!isChunk) {
			readCount += buffer.remaining();
			content.write(buffer.array(), buffer.position(), buffer.remaining());
			if (readCount >= total) {
				setCompleted(true);
			}
		} else {
			// 重置这两个变量
			if (firstChunk) {
				meetCR = false;
				changeLine = 0;
				firstChunk = false;
			}
			writeChunk(buffer);
		}
	}

	private ByteBuffer sizeBuffer = ByteBuffer.allocate(10);

	private void writeChunk(ByteBuffer buffer) {
		while (buffer.hasRemaining()) {
			if (readingChunkSize) {
				char c = (char) buffer.get();
				if (c == '\r') {
					meetCR = true;
				} else if (meetCR && c == '\n') {
					changeLine++;
				} else {
					changeLine = 0;
					meetCR = false;
					sizeBuffer.put((byte) c);
				}
			} else {
				content.write(buffer.get());
				readCount++;
				if (readCount >= total) {
					readingChunkSize = true;
				}
			}
			// 换行开始解析
			if (changeLine >= 1 && readingChunkSize) {
				sizeBuffer.flip();
				byte[] bytes = new byte[sizeBuffer.remaining()];
				sizeBuffer.get(bytes);
				String chunkSizeStr = new String(bytes).trim();
				if (chunkSizeStr != null && chunkSizeStr.length() > 0) {
					total = Long.parseLong(chunkSizeStr, 16);
				} else {
					// 空的时候，证明读了一个CRLF
					sizeBuffer.clear();
					continue;
				}
				if (total == 0) {
					setCompleted(true);
					break;
				}
				if(logger.isDebugEnabled()){
					logger.debug("chunk size:" + total);
				}
				readCount = 0;
				sizeBuffer.clear();
				readingChunkSize = false;
			}
		}
	}

	public boolean isCompleted() {
		return completed;
	}

	public void setCompleted(boolean completed) {
		this.completed = completed;
	}

	@Override
	public String toString() {
		String str = statuLine + "\r\n" + headers.toString() + "\r\n";
		String contentType = getHeader("Content-Type");
		String[] parts = contentType.split(";");
		String type = "text/html";
		String charSet = "utf-8";
		for (String string : parts) {
			int index = string.indexOf('=');
			if (index != -1) {
				charSet = string.substring(index + 1, string.length());
			} else {
				type = string.trim();
			}
		}
		if ("text/html".equals(type)) {
			try {
				str += new String(getContent(), charSet);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return str;
	}

	public String getCharset() {
		String contentType = getHeader("Content-Type");
		String charSet = "gbk";
		if(contentType == null){
			return charSet;
		}
		String[] parts = contentType.split(";");
		for (String string : parts) {
			int index = string.indexOf('=');
			if (index != -1) {
				charSet = string.substring(index + 1, string.length());
			}
		}
		return charSet;
	}
}
