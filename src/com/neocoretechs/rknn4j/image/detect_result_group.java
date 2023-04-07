package com.neocoretechs.rknn4j.image;
/**
 * Class to provide grouping of detected results of semantic segmentation and object detection
 * in a model agnostic manner.
 * @author Jonathan N. Groff Copyright (C) NeoCoreTechs 2023
 *
 */
public class detect_result_group {	
	int id;
	int count;
	detect_result results[];
	public detect_result_group() {	
	}
	
	public detect_result[] getResults() {
		return results;
	}
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder(id);
		sb.append("Count=");
		sb.append(count);
		sb.append("\r\n");
		if(results == null) {
			sb.append("No results");
		} else {
			for(int i = 0; i < results.length; i++) {
				sb.append(results[i]);
				sb.append("\r\n");
			}
		}
		return sb.toString();
	}
}
