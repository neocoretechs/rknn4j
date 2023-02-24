package com.neocoretechs.rknn4j.image;

public class detect_result_group {	
	int id;
	int count;
	detect_result results[];
	public detect_result_group() {	
	}
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder(id);
		sb.append("Count=");
		sb.append(count);
		sb.append("\r\n");
		for(int i = 0; i < results.length; i++) {
			sb.append(results[i]);
			sb.append("\r\n");
		}
		return sb.toString();
	}
}
