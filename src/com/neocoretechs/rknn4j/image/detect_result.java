package com.neocoretechs.rknn4j.image;

import java.awt.Rectangle;
import java.util.ArrayList;

/**
 * Postprocessing for the RockChip RK3588 NPU output models.<p/>
 * Processing depends on the particular model used, although many concepts are shared, specific detail regarding 
 * maximal suppression, etc. are unique to the particular model.<p/>
 * In the YOLOV5 model each object class is represented by (X,Y,W,H,Confidence) relative to the grid cell.
 * The X,Y position represents the upper left corner.
 * This is why you see the 5+Object_class references. A set of anchor boxes for each layer represents the
 * position that relative reference to cells use.
 * @author Jonathan Groff Copyright (C) NeoCoreTechs 2023
 *
 */
public class detect_result {
	String name;
	float prop;
	Rectangle box; //upper left x and y, width, height
	
	public static final int OBJ_CLASS_NUM  =   80;
	public static final double NMS_THRESH   =     0.45;
	public static final double BOX_THRESH   =     0.25;
	public static final int PROP_BOX_SIZE   = (5+OBJ_CLASS_NUM);
	
	// anchors for YOLOV5
	public static final int anchor0[] = {10, 13, 16, 30, 33, 23};
	public static final int anchor1[] = {30, 61, 62, 45, 59, 119};
	public static final int anchor2[] = {116, 90, 156, 198, 373, 326};
	
	@Override
	public String toString() {
		return String.format("Name=%s prop=%f x=%d,y=%d,width=%d,height=%d", name,prop,box.x,box.y,box.width,box.height);
	}
	
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
	 * Perform post processing on one of the output layers which was generated in want_float floating point format vs INT8
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
	 * @return Count of instances where max probability exceeded NMS threshold
	 */
	public static int process(byte[] input, int[] anchor, int grid_h, int grid_w, int height, int width, int stride,
            ArrayList<Float> boxes, ArrayList<Float> objProbs, ArrayList<Integer> classId, float threshold) {
		int    validCount = 0;
		int    grid_len   = grid_h * grid_w;
		float  thres      = (float) unsigmoid(threshold);
		for (int a = 0; a < 3; a++) {
			for (int i = 0; i < grid_h; i+=4) {
				for (int j = 0; j < grid_w; j+=4) {
					float box_confidence = extractFloat(input,(PROP_BOX_SIZE * a + 4) * grid_len + i * grid_w + j);
					if (box_confidence >= thres) {
						int     offset = (PROP_BOX_SIZE * a) * grid_len + i * grid_w + j;
						//int8_t* in_ptr = input + offset;
						//float   box_x  = sigmoid(deqnt_affine_to_f32(*in_ptr, zp, scale)) * 2.0 - 0.5;
						//float   box_y  = sigmoid(deqnt_affine_to_f32(in_ptr[grid_len], zp, scale)) * 2.0 - 0.5;
						//float   box_w  = sigmoid(deqnt_affine_to_f32(in_ptr[2 * grid_len], zp, scale)) * 2.0;
						//float   box_h  = sigmoid(deqnt_affine_to_f32(in_ptr[3 * grid_len], zp, scale)) * 2.0;
						float   box_x  = (float) (sigmoid(extractFloat(input, offset)) * 2.0 - 0.5);
						float   box_y  = (float) (sigmoid(extractFloat(input, offset+grid_len)) * 2.0 - 0.5);
						float   box_w  = (float) (sigmoid(extractFloat(input, offset + (2 * grid_len))) * 2.0);
						float   box_h  = (float) (sigmoid(extractFloat(input, offset + (3 * grid_len))) * 2.0);

						box_x          = (box_x + j) * (float)stride;
						box_y          = (box_y + i) * (float)stride;
						box_w          = box_w * box_w * (float)anchor[a * 2];
						box_h          = box_h * box_h * (float)anchor[a * 2 + 1];
						box_x -= (box_w / 2.0);
						box_y -= (box_h / 2.0);

						//int maxClassProbs = in_ptr[5 * grid_len];
						float maxClassProbs = extractFloat(input,offset + (5 * grid_len));
						int    maxClassId    = 0;
						for (int k = 1; k < OBJ_CLASS_NUM; ++k) {
							//int prob = in_ptr[(5 + k) * grid_len];
							float prob = extractFloat(input,offset+((5 + k) * grid_len));
							if (prob > maxClassProbs) {
								maxClassId    = k;
								maxClassProbs = prob;
							}
						}
						if (maxClassProbs>thres){
							objProbs.add((float)(sigmoid(maxClassProbs)* sigmoid(box_confidence)));
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
	/**
	 * Perform post processing on one of the output layers which was generated in INT8 default AFFINE format
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
	public static int process(byte[] input, int[] anchor, int grid_h, int grid_w, int height, int width, int stride,
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
							System.out.println("K="+k+" prob="+prob+" maxClassProbs="+maxClassProbs);
							if (prob > maxClassProbs) {
								maxClassId    = k;
								maxClassProbs = prob;
							}
						}
						System.out.println("maxClassProbs="+maxClassProbs+" thres_i8="+thres_i8);
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
	
	static int clamp(float val, int min, int max) { return (int) (val > min ? (val < max ? val : max) : min); }
	
	/**
	 * Perform post processing on YOLOv5 type result sets from neural processing unit.<p/>
	 * Data that conforms to the parameters of YOLOv5, in a 3 layer structure if int8 output.
	 * Scale_w and Scale_h seem to represent scaling if image and models differ, although images are resized
	 * its used to scale the final output boxes in the demo, hence it seems to be for drawing on an
	 * unresized version of the original image?
  	 * float scale_w = (float)width / img_width;
  	 * float scale_h = (float)height / img_height;
	 *
	 * @param input0 Layer 0 output from NPU
	 * @param input1 Layer 1 output from NPU
	 * @param input2 Layer 2 output from NPU
	 * @param model_in_h Model input height
	 * @param model_in_w Model input width
	 * @param conf_threshold confidence threshold
	 * @param nms_threshold Non maximal suppresion threshold
	 * @param scale_w Scale width see above
	 * @param scale_h Scale height see above
	 * @param qnt_zps zero point affine quantization factor
	 * @param qnt_scales affine scaling factor (goes with zp to convert int8 to float)
	 * @param group Output structure initialized to accept output groups
	 * @param labels the Class Id labels acquired from whatever source
	 * @return status of 0 for success, with detect_result_group populated with results array
	 */
	public static int post_process(byte[] input0, byte[] input1, byte[] input2, int model_in_h, int model_in_w, float conf_threshold,
            float nms_threshold, float scale_w, float scale_h, ArrayList<Integer> qnt_zps,
            ArrayList<Float> qnt_scales, detect_result_group group, String[] labels) {

		ArrayList<Float> filterBoxes0 = new ArrayList<Float>();
		ArrayList<Float> objProbs0 = new ArrayList<Float>();
		ArrayList<Integer> classId0 = new ArrayList<Integer>();
		
		ArrayList<Float> filterBoxes1 = new ArrayList<Float>();
		ArrayList<Float> objProbs1 = new ArrayList<Float>();
		ArrayList<Integer> classId1 = new ArrayList<Integer>();
		
		ArrayList<Float> filterBoxes2 = new ArrayList<Float>();
		ArrayList<Float> objProbs2 = new ArrayList<Float>();
		ArrayList<Integer> classId2 = new ArrayList<Integer>();

		// stride 8
		int stride0     = 8;
		int grid_h0     = model_in_h / stride0;
		int grid_w0     = model_in_w / stride0;
		int validCount0 = 0;
		validCount0 = process(input0, anchor0, grid_h0, grid_w0, model_in_h, model_in_w, stride0, filterBoxes0, objProbs0,
                   classId0, conf_threshold, qnt_zps.get(0), qnt_scales.get(0));

		// stride 16
		int stride1     = 16;
		int grid_h1     = model_in_h / stride1;
		int grid_w1     = model_in_w / stride1;
		int validCount1 = 0;
		validCount1 = process(input1, anchor1, grid_h1, grid_w1, model_in_h, model_in_w, stride1, filterBoxes1, objProbs1,
                   classId1, conf_threshold, qnt_zps.get(1), qnt_scales.get(1));

		// stride 32
		int stride2     = 32;
		int grid_h2     = model_in_h / stride2;
		int grid_w2     = model_in_w / stride2;
		int validCount2 = 0;
		validCount2 = process(input2, anchor2, grid_h2, grid_w2, model_in_h, model_in_w, stride2, filterBoxes2, objProbs2,
                   classId2, conf_threshold, qnt_zps.get(2), qnt_scales.get(2));

		int validCount = validCount0 + validCount1 + validCount2;
		System.out.printf("Valid count total =%d, 0=%d, 1=%d, 2=%d%n", validCount,validCount0,validCount1,validCount2);
		// no object detected
		if (validCount <= 0) {
			return 0;
		}

		int[] indexArray = new int[validCount];
		for (int i = 0; i < validCount; ++i) {
			indexArray[i] = i;
		}
		int[] objProbsArray =  new int[validCount];
		int i = 0;
		for(int j = 0; j < objProbs0.size(); j++) {
			objProbsArray[i++] = objProbs0.get(j).intValue();
		}
		for(int j = 0; j < objProbs1.size(); j++) {
			objProbsArray[i++] = objProbs1.get(j).intValue();
		}
		for(int j = 0; j < objProbs2.size(); j++) {
			objProbsArray[i++] = objProbs2.get(j).intValue();
		}
		System.out.println("Quicksort..");
		quick_sort_indice_inverse(objProbsArray, 0, validCount - 1, indexArray);
		System.out.println("Quicksort done");
		
		float[] filterBoxesArray = new float[validCount];
		System.out.println("Filterboxes 0="+filterBoxes0.size()+" 1="+filterBoxes1.size()+" 2="+filterBoxes2.size());
		i = 0;
		for(int j = 0; j < filterBoxes0.size(); j++) {
			filterBoxesArray[i++] = filterBoxes0.get(j);
		}
		for(int j = 0; j < filterBoxes1.size(); j++) {
			filterBoxesArray[i++] = filterBoxes1.get(j);
		}
		for(int j = 0; j < filterBoxes2.size(); j++) {
			filterBoxesArray[i++] = filterBoxes2.get(j);
		}
		
		int[] classIdArray = new int[validCount];
		System.out.println("ClassId 0="+classId0.size()+" 1="+classId1.size()+" 2="+classId2.size());
		i = 0;
		for(int j = 0; j < classId0.size(); j++) {
			classIdArray[i++] = classId0.get(j);
		}
		for(int j = 0; j < classId1.size(); j++) {
			classIdArray[i++] = classId1.get(j);
		}
		for(int j = 0; j < classId2.size(); j++) {
			classIdArray[i++] = classId2.get(j);
		}
		System.out.println("Arrays loaded");
		for(int j = 0; j < classIdArray.length; j++) {
			nms(validCount, filterBoxesArray, classIdArray, indexArray, classIdArray[j], nms_threshold);
		}
		System.out.println("Maximal suppression done");
		ArrayList<detect_result> groupArray = new ArrayList<detect_result>();
		/* box valid detect target */
		i = 0;
		for(; i < validCount; ++i) {
			if (indexArray[i] == -1) {
				continue;
			}
			int n = indexArray[i];
			float x1       = filterBoxesArray[n * 4 + 0];
			float y1       = filterBoxesArray[n * 4 + 1];
			float x2       = x1 + filterBoxesArray[n * 4 + 2];
			float y2       = y1 + filterBoxesArray[n * 4 + 3];
			int   id       = classIdArray[n];
			float obj_conf = objProbsArray[i];
			detect_result dr = new detect_result();
			dr.box.x   = (int)(clamp(x1, 0, model_in_w) / scale_w);
			dr.box.y    = (int)(clamp(y1, 0, model_in_h) / scale_h);
			dr.box.width  = (int)(clamp(x2, 0, model_in_w) / scale_w);
			dr.box.height = (int)(clamp(y2, 0, model_in_h) / scale_h);
			dr.prop       = obj_conf;
			dr.name		  = labels[id];
			groupArray.add(dr);

		System.out.printf("result %2d: (%4d, %4d, %4d, %4d), %s %s\n", i, dr.box.x,dr.box.y,dr.box.width,dr.box.height,dr.prop,dr.name);

		}
		//group.id
		group.count = groupArray.size();
		group.results = new detect_result[groupArray.size()];
		i = 0;
		for(; i < groupArray.size(); i++) {
			group.results[i] = groupArray.get(i);
		}
		return 0;
	}

}
