package com.neocoretechs.rknn4j.image;

import java.awt.Rectangle;
import java.util.ArrayList;


public class detect_result {
	String name;
	float prop;
	Rectangle box; //upper left x and y, width, height
	
	public static final int OBJ_CLASS_NUM  =   80;
	public static final double NMS_THRESH   =     0.45;
	public static final double BOX_THRESH   =     0.25;
	public static final int PROP_BOX_SIZE   = (5+OBJ_CLASS_NUM);
	
	static float CalculateOverlap(float xmin0, float ymin0, float xmax0, float ymax0, float xmin1, float ymin1, float xmax1, float ymax1) {
		double w = Math.max(0.f, Math.min(xmax0, xmax1) - Math.max(xmin0, xmin1) + 1.0);
		double h = Math.max(0.f, Math.min(ymax0, ymax1) - Math.max(ymin0, ymin1) + 1.0);
		double i = w * h;
		double u = (xmax0 - xmin0 + 1.0) * (ymax0 - ymin0 + 1.0) + (xmax1 - xmin1 + 1.0) * (ymax1 - ymin1 + 1.0) - i;
		return (float) (u <= 0.f ? 0.f : (i / u));
	}
	/**
	 * Perform NMS (Non-Maximal Suppression) to eliminate overlapping bounding boxes
	 * @param validCount
	 * @param outputLocations
	 * @param classIds
	 * @param order
	 * @param filterId
	 * @param threshold
	 * @return
	 */
	static int nms(int validCount, float[] outputLocations, int[] classIds, int[] order, int filterId, float threshold) {
		for (int i = 0; i < validCount; ++i) {
			if (order[i] == -1 || classIds[i] != filterId) {
				continue;
			}
			int n = order[i];
			for (int j = i + 1; j < validCount; ++j) {
				int m = order[j];
				if (m == -1 || classIds[i] != filterId) {
					continue;
				}
				float xmin0 = outputLocations[n * 4 + 0];
				float ymin0 = outputLocations[n * 4 + 1];
				float xmax0 = outputLocations[n * 4 + 0] + outputLocations[n * 4 + 2];
				float ymax0 = outputLocations[n * 4 + 1] + outputLocations[n * 4 + 3];

				float xmin1 = outputLocations[m * 4 + 0];
				float ymin1 = outputLocations[m * 4 + 1];
				float xmax1 = outputLocations[m * 4 + 0] + outputLocations[m * 4 + 2];
				float ymax1 = outputLocations[m * 4 + 1] + outputLocations[m * 4 + 3];

				float iou = CalculateOverlap(xmin0, ymin0, xmax0, ymax0, xmin1, ymin1, xmax1, ymax1);

				if (iou > threshold) {
					order[j] = -1;
				}
			}
		}
		return 0;
	}
	
	static double sigmoid(float x) { return 1.0 / (1.0 + Math.exp(-x)); }

	static double unsigmoid(float y) { return -1.0 * Math.log((1.0 / y) - 1.0); }
	/**
	 * If val <= min, return min cast to int, else if val >= max return val cast to int
	 * @param val
	 * @param min
	 * @param max
	 * @return
	 */
	static int clip(float val, float min, float max) {
	  float f = val <= min ? min : (val >= max ? max : val);
	  return (int) f;
	}
	/**
	 * Divide f32 by scale and add zp, then perform clip on that using values -128, 127
	 * @param f32
	 * @param zp
	 * @param scale
	 * @return
	 */
	static int qnt_f32_to_affine(float f32, int zp, float scale) {
	  float  dst_val = (f32 / scale) + zp;
	  int res = clip(dst_val, -128, 127);
	  return res;
	}
	/**
	 * (float qnt - float zp) multiplied by scale
	 * @param qnt
	 * @param zp
	 * @param scale
	 * @return (float qnt - float zp) multiplied by scale
	 */
	static float deqnt_affine_to_f32(int qnt, int zp, float scale) { 
		return ((float)qnt - (float)zp) * scale; 
	}
	
	static int quick_sort_indice_inverse(int[] input, int left, int right, int[] indices) {
	  float key;
	  int   key_index;
	  int   low  = left;
	  int   high = right;
	  if (left < right) {
	    key_index = indices[left];
	    key       = input[left];
	    while (low < high) {
	      while (low < high && input[high] <= key) {
	        high--;
	      }
	      input[low]   = input[high];
	      indices[low] = indices[high];
	      while (low < high && input[low] >= key) {
	        low++;
	      }
	      input[high]   = input[low];
	      indices[high] = indices[low];
	    }
	    input[low]   = (int) key;
	    indices[low] = key_index;
	    quick_sort_indice_inverse(input, left, low - 1, indices);
	    quick_sort_indice_inverse(input, low + 1, right, indices);
	  }
	  return low;
	}
	
	static float extractFloat(byte[] buffer, int ptr, int zp, float scale) {
		return deqnt_affine_to_f32(buffer[ptr], zp, scale);
	}
	static float extractFloat(byte[] buffer, int n) {
		return Float.intBitsToFloat( buffer[n] ^ buffer[n+1]<<8 ^ buffer[n+2]<<16 ^ buffer[n+3]<<24 );
	}
	/**
	 * Perform post processing on one of the output layers
	 * @param input Input byte buffer from NPU run
	 * @param anchor float array of anchor boxes
	 * @param grid_h grid height
	 * @param grid_w grid width
	 * @param height height from model
	 * @param width width from model
	 * @param stride stride from model
	 * @param boxes float collection of boxes populated by method
	 * @param objProbs float collection of object probabilities populated by method
	 * @param classId int collection of class Id indexes populated by method
	 * @param threshold Non Maximal Suppression threshold constant to pass to unsigmoid function to determine confidence in box overlap
	 * @param zp Affine conversion factor compressing float to INT8 Zero Point offset for quantization
	 * @param scale Used to map input range to output range for quantization of float to INT8
	 * @return Count of instances where max probability exceeded NMS threshold
	 */
	static int process(byte[] input, int[] anchor, int grid_h, int grid_w, int height, int width, int stride,
            ArrayList<Float> boxes, ArrayList<Float> objProbs, ArrayList<Integer> classId, float threshold, int zp, float scale) {
		int    validCount = 0;
		int    grid_len   = grid_h * grid_w;
		float  thres      = (float) unsigmoid(threshold);
		int thres_i8   = qnt_f32_to_affine(thres, zp, scale);
		for (int a = 0; a < 3; a++) {
			for (int i = 0; i < grid_h; i++) {
				for (int j = 0; j < grid_w; j++) {
					int box_confidence = input[(PROP_BOX_SIZE * a + 4) * grid_len + i * grid_w + j];
					if (box_confidence >= thres_i8) {
						int     offset = (PROP_BOX_SIZE * a) * grid_len + i * grid_w + j;
						//int8_t* in_ptr = input + offset;
						//float   box_x  = sigmoid(deqnt_affine_to_f32(*in_ptr, zp, scale)) * 2.0 - 0.5;
						//float   box_y  = sigmoid(deqnt_affine_to_f32(in_ptr[grid_len], zp, scale)) * 2.0 - 0.5;
						//float   box_w  = sigmoid(deqnt_affine_to_f32(in_ptr[2 * grid_len], zp, scale)) * 2.0;
						//float   box_h  = sigmoid(deqnt_affine_to_f32(in_ptr[3 * grid_len], zp, scale)) * 2.0;
						float   box_x  = (float) (sigmoid(extractFloat(input, offset, zp, scale)) * 2.0 - 0.5);
						float   box_y  = (float) (sigmoid(extractFloat(input, offset+grid_len, zp, scale)) * 2.0 - 0.5);
						float   box_w  = (float) (sigmoid(extractFloat(input, offset + (2 * grid_len), zp, scale)) * 2.0);
						float   box_h  = (float) (sigmoid(extractFloat(input, offset + (3 * grid_len), zp, scale)) * 2.0);

						box_x          = (box_x + j) * (float)stride;
						box_y          = (box_y + i) * (float)stride;
						box_w          = box_w * box_w * (float)anchor[a * 2];
						box_h          = box_h * box_h * (float)anchor[a * 2 + 1];
						box_x -= (box_w / 2.0);
						box_y -= (box_h / 2.0);

						//int maxClassProbs = in_ptr[5 * grid_len];
						int maxClassProbs = input[offset + (5 * grid_len)];
						int    maxClassId    = 0;
						for (int k = 1; k < OBJ_CLASS_NUM; ++k) {
							//int prob = in_ptr[(5 + k) * grid_len];
							int prob = input[offset+((5 + k) * grid_len)];
							if (prob > maxClassProbs) {
								maxClassId    = k;
								maxClassProbs = prob;
							}
						}
						if (maxClassProbs>thres_i8){
							objProbs.add((float)(sigmoid(deqnt_affine_to_f32(maxClassProbs, zp, scale))* sigmoid(deqnt_affine_to_f32(box_confidence, zp, scale))));
							classId.add(maxClassId);
							validCount++;
							boxes.add(box_x);
							boxes.add(box_y);
							boxes.add(box_w);
							boxes.add(box_h);
						}
					}
				}
			}
		}
		return validCount;
	}


}
