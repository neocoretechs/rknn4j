package com.neocoretechs.rknn4j.image;

import java.util.Arrays;
import java.util.Objects;

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
	public int getCount() {
		return count;
	}
	/**
	 * {
  	 * "count": 2,
  	 * "detections": [
     * {
     * "name": "person",
     * "probability": 0.30,
     * "bbox": {"xmin": 200, "ymin": 200, "xmax": 300, "ymax": 300}
     * },
     * {
     * "name": "dog",
     * "probability": 0.40,
     * "bbox": {"xmin": 120, "ymin": 180, "xmax": 240, "ymax": 360}
     * }
   	 * ]
	 * }
	 * @return
	 */
	public String toJson() {
		StringBuilder sb = new StringBuilder("{\r\n\"count\":");
		sb.append(count);
		sb.append(",\r\n\"detections\":[\r\n");
		if(results != null) 
			for (int i = 0; i < results.length; i++) {
			    if(i > 0 && i < results.length-1)
			    	sb.append(",\r\n");
			    sb.append(results[i].toJson());
			}
		sb.append("]\r\n}");
		return sb.toString();
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

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + Arrays.hashCode(results);
		result = prime * result + Objects.hash(count);
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!(obj instanceof detect_result_group)) {
			return false;
		}
		detect_result_group other = (detect_result_group) obj;
		return count == other.count && Arrays.equals(results, other.results);
	}
}
