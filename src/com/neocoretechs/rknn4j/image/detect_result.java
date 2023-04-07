package com.neocoretechs.rknn4j.image;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Arrays;

import com.neocoretechs.rknn4j.rknn_output;

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
	private static boolean DEBUG = false;
	private static boolean DEBUG_VERBOSE = false;
	String name;
	float probability;
	Rectangle box; //upper left x and y, width, height
	
	public static final int OBJ_CLASS_NUM  =   80;
	public static final float NMS_THRESH   = 0.45f;
	public static final float BOX_THRESH   = 0.15f;//   0.25f;
	public static final int PROP_BOX_SIZE   = (5+OBJ_CLASS_NUM);
	
	// constants for SSD
	public static final int NUM_RESULTS = 1917;
	public static float MIN_SCORE     = .15f;//0.4f;
	public static int NUM_CLASS = 91;
	public static final float NMS_THRESH_SSD   = 0.5f;
	
	// anchors for YOLOV5
	public static final int anchor0[] = {10, 13, 16, 30, 33, 23};
	public static final int anchor1[] = {30, 61, 62, 45, 59, 119};
	public static final int anchor2[] = {116, 90, 156, 198, 373, 326};
	
	// Inception SSD scales for decode_center_size_boxes
	public static final float Y_SCALE = 10.0f;
	public static final float X_SCALE = 10.0f;
	public static final float H_SCALE = 5.0f;
	public static final float W_SCALE = 5.0f;

	public detect_result() {
		box = new Rectangle();
	}
	
	@Override
	public String toString() {
		return String.format("Name=%s probability=%f xmin=%d,ymin=%d,xmax=%d,ymax=%d", name,probability,box.xmin,box.ymin,box.xmax,box.ymax);
	}
	
	public static class Rectangle {
		int xmin;
		int ymin;
		int xmax;
		int ymax;
	}
	
	/**
	 * @return the name
	 */
	public String getName() {
		return name;
	}

	/**
	 * @return the probability
	 */
	public float getProbability() {
		return probability;
	}

	/**
	 * @return the box
	 */
	public Rectangle getBox() {
		return box;
	}

	/**
	 * IoU - intersection over union calculation for Non-Maximal Suppression.
	 * For area of intersecting part, 
	 * x_distance for intersecting rectangle = min(xmax0, xmax1) – max(xmin0, xmin1) 
	 * y_distance for 1st rectangle = min(ymax0, ymax1) – max(ymin0, ymin1)
	 * If the x_distance or y_distance is negative, then the two rectangles do not intersect.
	 * In that case, overlapping area is 0.<p/>
	 * Length of intersecting part i.e start from max(xmin0, xmin1) of x-coordinate and end at min(xmax0,xmax1) 
	 * x-coordinate by subtracting start from end we get required lengths.<p/>
	 * i = w * h = total area of intersecting rectangle;<p/>
	 * Total Area = (Area of 1st rectangle + Area of 2nd rectangle) -  Area of Intersecting part.<p/>
	 * We lock total area of intersection to >=0 by taking max of (0, 1 + length). We then compute area of both rectangles, 
	 * by:<br/>u = width0+1 * height0+1 + width1+1 * height1+1 - i <p/>
	 * We then return 0 if the Total Area calc u is <= 0, otherwise, return ratio of area of intersection over total area i/u.
	 * intersection of areas over union of areas. Remove boxes with IoU over threshold.
	 * @param xmin0
	 * @param ymin0
	 * @param xmax0
	 * @param ymax0
	 * @param xmin1
	 * @param ymin1
	 * @param xmax1
	 * @param ymax1
	 * @return
	 */
	static float calculateOverlapYOLO(float xmin0, float ymin0, float xmax0, float ymax0, float xmin1, float ymin1, float xmax1, float ymax1) {
		float w = Math.max(0.0f, Math.min(xmax0, xmax1) - Math.max(xmin0, xmin1) + 1.0f);
		float h = Math.max(0.0f, Math.min(ymax0, ymax1) - Math.max(ymin0, ymin1) + 1.0f);
		float i = w * h; // total area of intersection rectangle
		float u = (xmax0 - xmin0 + 1.0f) * (ymax0 - ymin0 + 1.0f) + (xmax1 - xmin1 + 1.0f) * (ymax1 - ymin1 + 1.0f) - i;
		return (u <= 0.f ? 0.f : (i / u));
	}
	
	static float calculateOverlapSSD(float xmin0, float ymin0, float xmax0, float ymax0, float xmin1, float ymin1, float xmax1, float ymax1) {
		float w = Math.max(0.0f, Math.min(xmax0, xmax1) - Math.max(xmin0, xmin1));
		float h = Math.max(0.0f, Math.min(ymax0, ymax1) - Math.max(ymin0, ymin1));
		float i = w * h;
		float u = (xmax0 - xmin0) * (ymax0 - ymin0) + (xmax1 - xmin1) * (ymax1 - ymin1) - i;
		return (u <= 0.f ? 0.f : (i / u));
	}
	
	/**
	 * InceptionSSD detection box anchors
	 * @param predictions Populated array which will be re-ordered
	 * @param boxPriors Populated array which will be read-only
	 */
	static void decodeCenterSizeBoxes(float[] predictions, float[][] boxPriors) {
	  for (int i = 0; i < NUM_RESULTS; i++) {
	    float ycenter = predictions[i * 4 + 0] / Y_SCALE * boxPriors[2][i] + boxPriors[0][i];
	    float xcenter = predictions[i * 4 + 1] / X_SCALE * boxPriors[3][i] + boxPriors[1][i];
	    float h       = (float)Math.exp(predictions[i * 4 + 2] / H_SCALE) * boxPriors[2][i];
	    float w       = (float)Math.exp(predictions[i * 4 + 3] / W_SCALE) * boxPriors[3][i];

	    float ymin = ycenter - h / 2.0f;
	    float xmin = xcenter - w / 2.0f;
	    float ymax = ycenter + h / 2.0f;
	    float xmax = xcenter + w / 2.0f;

	    predictions[i * 4 + 0] = ymin;
	    predictions[i * 4 + 1] = xmin;
	    predictions[i * 4 + 2] = ymax;
	    predictions[i * 4 + 3] = xmax;
	  }
	}
	/**
	 * 
	 * @param outputClasses
	 * @param output
	 * @param numClasses
	 * @param props
	 * @return validResult
	 */
	static int filterValidResult(float[] outputClasses, int[][] output, int numClasses, float[] props) {
	  int   validCount = 0;
	  float min_score  = (float) unsigmoid(MIN_SCORE);
	  // Scale them back to the input size.
	  for (int i = 0; i < NUM_RESULTS; i++) {
	    float topClassScore      = -1000.0f;
	    int   topClassScoreIndex = -1;
	    // Skip the first catch-all class.
	    for (int j = 1; j < numClasses; j++) {
	      // x and sigmoid(x) has same monotonicity
	      // so compare x and compare sigmoid(x) is same
	      // float score = (float) sigmoid(outputClasses[i*numClasses+j]);
	      float score = outputClasses[i * numClasses + j];
	      if(DEBUG)
	    	  System.out.println("filterValidResult Result#="+i+" Class#="+j+" Score="+score+" min_score="+min_score);
	      if (score > topClassScore) {
	    	  if(DEBUG)
	    		  System.out.println("**filterValidResult New top class score Result#="+i+" topClassScoreIndex#="+j+" topClassScore="+score+" min_score="+min_score);
	        topClassScoreIndex = j;
	        topClassScore      = score;
	      }
	    }
	    if (topClassScore >= min_score) {
	      output[0][validCount] = i;
	      output[1][validCount] = topClassScoreIndex;
	      props[validCount]     = (float) sigmoid(outputClasses[i * numClasses + topClassScoreIndex]);
	      if(DEBUG) {
	    	  System.out.println("***filterValidResult New output validCount="+validCount+" output[0]="+i+" output[1]="+topClassScoreIndex+" props[validCount]="+props[validCount]+" topClassSCore="+topClassScore+" min_score="+min_score);
	      }
	      ++validCount;
	    }
	  }
	  return validCount;
	}
	/**
	 * Perform NMS (Non-Maximal Suppression) to eliminate overlapping bounding boxes in the YOLO model
	 * @param validCount
	 * @param outputLocations
	 * @param classIds
	 * @param order
	 * @param filterId
	 * @param threshold
	 */
	static void nmsYOLO(int validCount, float[] outputLocations, int[] classIds, int[] order, int filterId, float threshold) {
		for (int i = 0; i < validCount; i++) {
			int n = order[i];
			if (n == -1 || classIds[i] != filterId) {
				continue;
			}
			float xmin0 = outputLocations[n * 4 + 0];
			float ymin0 = outputLocations[n * 4 + 1];
			float xmax0 = outputLocations[n * 4 + 0] + outputLocations[n * 4 + 2];
			float ymax0 = outputLocations[n * 4 + 1] + outputLocations[n * 4 + 3];

			for (int j = i + 1; j < validCount; j++) {
				int m = order[j];
				if (m == -1) {
					continue;
				}

				float xmin1 = outputLocations[m * 4 + 0];
				float ymin1 = outputLocations[m * 4 + 1];
				float xmax1 = outputLocations[m * 4 + 0] + outputLocations[m * 4 + 2];
				float ymax1 = outputLocations[m * 4 + 1] + outputLocations[m * 4 + 3];

				float iou = calculateOverlapYOLO(xmin0, ymin0, xmax0, ymax0, xmin1, ymin1, xmax1, ymax1);

				if (iou > threshold) {
					order[j] = -1;
				}
			}
		}
	}
	
	/**
	 * Perform NMS (Non-Maximal Suppression) to eliminate overlapping bounding boxes in the InceptionSSD model.
	 * @param validCount
	 * @param outputLocations
	 * @param output
	 * @param threshold
	 */
	static void nmsSSD(int validCount, float[] outputLocations, int[][] output, float threshold) {
	  for (int i = 0; i < validCount; i++) {
	    if (output[0][i] == -1) {
	      continue;
	    }
	    int n = output[0][i];
	    for (int j = i + 1; j < validCount; j++) {
	      int m = output[0][j];
	      if (m == -1) {
	        continue;
	      }
	      float xmin0 = outputLocations[n * 4 + 1];
	      float ymin0 = outputLocations[n * 4 + 0];
	      float xmax0 = outputLocations[n * 4 + 3];
	      float ymax0 = outputLocations[n * 4 + 2];

	      float xmin1 = outputLocations[m * 4 + 1];
	      float ymin1 = outputLocations[m * 4 + 0];
	      float xmax1 = outputLocations[m * 4 + 3];
	      float ymax1 = outputLocations[m * 4 + 2];

	      float iou = calculateOverlapSSD(xmin0, ymin0, xmax0, ymax0, xmin1, ymin1, xmax1, ymax1);

	      if (iou >= threshold) {
				if(DEBUG)
					System.out.printf("nmsSSD Source=%d, target=%d xmin0=%f ymin0=%f xmax0=%f ymax0=%f xmin1=%f ymin1=%f xmax1=%f ymax1=%f iou=%f >= threshold=%f overwriting output[0][j]=%d%n",i,j,xmin0,ymin0,xmax0,ymax0,xmin1,ymin1,xmax1,ymax1,iou,threshold,output[0][j]);
	        output[0][j] = -1;
	      }
	    }
	  }
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
	static byte qnt_f32_to_affine(float f32, int zp, float scale) {
	  float  dst_val = (f32 / scale) + zp;
	  byte res = (byte)clip(dst_val, -128, 127);
	  return res;
	}
	/**
	 * (float qnt - float zp) multiplied by scale
	 * @param qnt
	 * @param zp
	 * @param scale
	 * @return (float qnt - float zp) multiplied by scale
	 */
	static float deqnt_affine_to_f32(byte qnt, int zp, float scale) { 
		return ((float)qnt - (float)zp) * scale; 
	}
	
	static float extractFloat(byte[] buffer, int ptr, int zp, float scale) {
		return deqnt_affine_to_f32(buffer[ptr], zp, scale);
	}

	/**
	 * Perform a quicksort on the indexes involved in the YOLO model.
	 * @param input
	 * @param left
	 * @param right
	 * @param indices
	 * @return
	 */
	static int quick_sort_indice_inverse(float[] input, int left, int right, int[] indices) {
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
	    input[low]   = key;
	    indices[low] = key_index;
	    quick_sort_indice_inverse(input, left, low - 1, indices);
	    quick_sort_indice_inverse(input, low + 1, right, indices);
	  }
	  return low;
	}
	/**
	 * Sort the InceptSSD parameters
	 * @param output
	 * @param props
	 * @param sz
	 */
	static void sort(int[][] output, float[] props, int sz) {
	  int i = 0;
	  int j = 0;
	  if (sz < 2) {
	    return;
	  }
	  for (i = 0; i < sz - 1; i++) {
	    int top = i;
	    for (j = i + 1; j < sz; j++) {
	      if (props[top] < props[j]) {
	        top = j;
	      }
	    }
	    if (i != top) {
	      int   tmp1     = output[0][i];
	      int   tmp2     = output[1][i];
	      float prop     = props[i];
	      output[0][i]   = output[0][top];
	      output[1][i]   = output[1][top];
	      props[i]       = props[top];
	      output[0][top] = tmp1;
	      output[1][top] = tmp2;
	      props[top]     = prop;
	    }
	  }
	}
	
	/**
	 * FLOAT <p/>
	 * Perform post processing on the InceptionSSD output layers which was generated in INT8
	 * @param input0 Input byte buffer from NPU run convert to predictions
	 * @param qnt_scale 
	 * @param qnt_zp 
	 */
	public static float[] process(byte[] input0, Integer qnt_zp, Float qnt_scale) {
		float[] floatArray = new float[input0.length];
		for(int i = 0; i < input0.length; i++)
			floatArray[i] = extractFloat(input0, i, qnt_zp, qnt_scale);
		return floatArray;
	}
	/**
	 * FLOAT <p/>
	 * Perform post processing on the InceptionSSD output layers which was generated in want_float floating point format vs INT8
	 * @param input0 Input byte buffer from NPU run convert to predictions
	 */
	public static float[] process(byte[] input0) {
		ByteBuffer byteBuffer0 = ByteBuffer.wrap(input0);
		if(DEBUG)
			System.out.println("Byte order:"+ByteOrder.nativeOrder());
		ByteBuffer byteBuffer = byteBuffer0.order(ByteOrder.nativeOrder());
		byteBuffer.rewind();
		float[] floatArray = new float[input0.length / 4];
		byteBuffer.asFloatBuffer().get(floatArray);
		if(DEBUG_VERBOSE) {
			System.out.println("SSD process ="+floatArray.length);
			System.out.println(Arrays.toString(floatArray));
		}
		return floatArray;
	}
	/**
	 * Process the InceptSSD arrays assembled from post processing output layers after extracting buffer data and
	 * assembling regions and probabilities. Perform the non-maximal suppression and populate detect_result_group group object.
	 * @param output 2 Layers output from NPU
	 * @param predictionsArray Array of detection boxes
	 * @param props Array of object probabilities
	 * @param labels category labels
	 * @param group Instance of detect_result_group to be populated by processing
	 * @param nms_threshold Non-Maximal suppression threshold
	 * @param validCount Count of current valid (detected) prospective objects from the NPU
	 * @param model_in_w width of model input
	 * @param model_in_h height of model input
	 * @param scale_w
	 * @param scale_h
	 * @return The size of the detected objects collection after processing
	 */
	static int process_arraysSSD(int[][] output, float[] predictionsArray, float[] props, String[] labels, 
			detect_result_group group, float nms_threshold, int validCount, int model_in_w, int model_in_h, float scale_w, float scale_h) {
		//
		nmsSSD(validCount, predictionsArray, output, nms_threshold);
		if(DEBUG)
			System.out.println("NonMaximal suppression done filterBoxesArray:"+Arrays.toString(predictionsArray));
	
		ArrayList<detect_result> groupArray = new ArrayList<detect_result>();
		/* box valid detect target */
		for (int i = 0; i < validCount; i++) {
			if (output[0][i] == -1) {
				continue;
			}
			int n = output[0][i];
		    int topClassScoreIndex = output[1][i];

		    int x1 = (int)(predictionsArray[n * 4 + 1] * model_in_w);
		    int y1 = (int)(predictionsArray[n * 4 + 0] * model_in_h);
		    int x2 = (int)(predictionsArray[n * 4 + 3] * model_in_w);
		    int y2 = (int)(predictionsArray[n * 4 + 2] * model_in_h);
		    // There's a bug that always shows toothbrush?
		    if (x1 == 0 && x2 == 0 && y1 == 0 && y2 == 0)
		    	continue;
			if(DEBUG)
				System.out.println("x1="+x1+" y1="+y1+" x2="+x2+" y2="+y2+" id="+n+" toClassScoreIndex="+topClassScoreIndex);
			detect_result dr = new detect_result();
		    dr.box.xmin = x1;
		    dr.box.ymin = y1;
		    dr.box.xmax = x2;
		    dr.box.ymax = y2;
		    dr.probability = props[i];
		    dr.name = labels[topClassScoreIndex];
		    if(DEBUG)
		    	System.out.printf("ssd result %2d: (%4d, %4d, %4d, %4d), %4.2f, %s\n", i, x1, y1, x2, y2, props[i], labels[topClassScoreIndex]);
		    groupArray.add(dr);
		}
		//group.id
		group.count = groupArray.size();
		group.results = new detect_result[groupArray.size()];
		for(int i = 0; i < groupArray.size(); i++) {
			group.results[i] = groupArray.get(i);
		}
		return groupArray.size();
	}
	/**
	 * INT8 AFFINE <p/>
	 * Perform post processing on one of the output layers which was generated in INT8 default AFFINE format
	 * @param input Input byte buffer from NPU run
	 * @param anchor float array of anchor boxes
	 * @param grid_h grid height
	 * @param grid_w grid width
	 * @param height height from model
	 * @param width width from model
	 * @param stride stride from model
	 * @param boxes float collection of boxes populated by method x,y,width,height
	 * @param objProbs float collection of object probabilities populated by method
	 * @param classId int collection of class Id indexes populated by method
	 * @param threshold Box threshold constant to pass to unsigmoid function to determine confidence in box overlap
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
					byte box_confidence = input[(PROP_BOX_SIZE * a + 4) * grid_len + i * grid_w + j];
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
						if(DEBUG )
							System.out.printf("Extracted raw coords %f %f %f %f%n", box_x,box_y,box_w,box_h);
						box_x          = (box_x + j) * (float)stride;
						box_y          = (box_y + i) * (float)stride;
						box_w          = box_w * box_w * (float)anchor[a * 2];
						box_h          = box_h * box_h * (float)anchor[a * 2 + 1];
						box_x -= (box_w / 2.0);
						box_y -= (box_h / 2.0);
						if(DEBUG )
							System.out.printf("Processed raw coords %f %f %f %f%n", box_x,box_y,box_w,box_h);
						//int maxClassProbs = in_ptr[5 * grid_len];
						byte maxClassProbs = input[offset + (5 * grid_len)];
						int    maxClassId    = 0;
						for (int k = 1; k < OBJ_CLASS_NUM; k++) {
							//int prob = in_ptr[(5 + k) * grid_len];
							byte prob = input[offset+((5 + k) * grid_len)];
							if(DEBUG_VERBOSE )
								System.out.println("K="+k+" prob="+prob+" maxClassProbs="+maxClassProbs);
							if (prob > maxClassProbs) {
								if(DEBUG_VERBOSE)
									System.out.println("****K="+k+" prob="+prob+" maxClassProbs="+maxClassProbs);
								maxClassId    = k;
								maxClassProbs = prob;
							}
						}
						if(DEBUG_VERBOSE)
							System.out.println("maxClassProbs="+maxClassProbs+" thres_i8="+thres_i8+" box_conf="+box_confidence+" maxClassId="+maxClassId);
						if (maxClassProbs > thres_i8){
							float fProb = (float)(sigmoid(deqnt_affine_to_f32(maxClassProbs, zp, scale)));
							float boxConf = (float)(sigmoid(deqnt_affine_to_f32(box_confidence, zp, scale)));
							if(DEBUG)
								System.out.println("Float prob="+fProb+" boxConf="+boxConf);
							objProbs.add(fProb*boxConf);
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
	 * FLOAT <p/>
	 * Perform post processing on one of the output layers which was generated in FLOAT format (YOLO)
	 * @param input Input float buffer from NPU run
	 * @param anchor float array of anchor boxes
	 * @param grid_h grid height
	 * @param grid_w grid width
	 * @param height height from model
	 * @param width width from model
	 * @param stride stride from model
	 * @param boxes float collection of boxes populated by method x,y,width,height
	 * @param objProbs float collection of object probabilities populated by method
	 * @param classId int collection of class Id indexes populated by method
	 * @param threshold Box threshold constant to pass to unsigmoid function to determine confidence in box overlap
	 * @param zp Affine conversion factor compressing float to INT8 Zero Point offset for quantization
	 * @param scale Used to map input range to output range for quantization of float to INT8
	 * @return Count of instances where max probability exceeded NMS threshold
	 */
	public static int process(float[] input, int[] anchor, int grid_h, int grid_w, int height, int width, int stride,
            ArrayList<Float> boxes, ArrayList<Float> objProbs, ArrayList<Integer> classId, float threshold) {
		int    validCount = 0;
		int    grid_len   = grid_h * grid_w;
		float  thres      = (float) unsigmoid(threshold);
		for (int a = 0; a < 3; a++) {
			for (int i = 0; i < grid_h; i++) {
				for (int j = 0; j < grid_w; j++) {
					float box_confidence = input[(PROP_BOX_SIZE * a + 4) * grid_len + i * grid_w + j];
					if (box_confidence >= thres) {
						int     offset = (PROP_BOX_SIZE * a) * grid_len + i * grid_w + j;
						float   box_x  = (float) (sigmoid(input[offset]) * 2.0 - 0.5);
						float   box_y  = (float) (sigmoid(input[offset+grid_len]) * 2.0 - 0.5);
						float   box_w  = (float) (sigmoid(input[offset + (2 * grid_len)]) * 2.0);
						float   box_h  = (float) (sigmoid(input[offset + (3 * grid_len)]) * 2.0);
						if(DEBUG )
							System.out.printf("Extracted raw coords %f %f %f %f%n", box_x,box_y,box_w,box_h);
						box_x          = (box_x + j) * (float)stride;
						box_y          = (box_y + i) * (float)stride;
						box_w          = box_w * box_w * (float)anchor[a * 2];
						box_h          = box_h * box_h * (float)anchor[a * 2 + 1];
						box_x -= (box_w / 2.0);
						box_y -= (box_h / 2.0);
						if(DEBUG )
							System.out.printf("Processed raw coords %f %f %f %f%n", box_x,box_y,box_w,box_h);
						float maxClassProbs = input[offset + (5 * grid_len)];
						int    maxClassId    = 0;
						for (int k = 1; k < OBJ_CLASS_NUM; k++) {
							float prob = input[offset+((5 + k) * grid_len)];
							if(DEBUG_VERBOSE )
								System.out.println("K="+k+" prob="+prob+" maxClassProbs="+maxClassProbs);
							if (prob > maxClassProbs) {
								if(DEBUG_VERBOSE)
									System.out.println("****K="+k+" prob="+prob+" maxClassProbs="+maxClassProbs);
								maxClassId    = k;
								maxClassProbs = prob;
							}
						}
						if(DEBUG_VERBOSE)
							System.out.println("maxClassProbs="+maxClassProbs+" thres_i8="+thres+" box_conf="+box_confidence+" maxClassId="+maxClassId);
						if (maxClassProbs > thres){
							float fProb = (float)(sigmoid(maxClassProbs));
							float boxConf = (float)(sigmoid(box_confidence));
							if(DEBUG)
								System.out.println("Float prob="+fProb+" boxConf="+boxConf);
							objProbs.add(fProb*boxConf);
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
	 * Process the YOLO arrays assembled from post processing output layers after extracting buffer data and
	 * assembling regions and probabilities. Perform the non-maximal suppression and populate detect_result_group group object.
	 * @param classIdArray
	 * @param indexArray
	 * @param filterBoxesArray
	 * @param objProbsArray
	 * @param labels
	 * @param group
	 * @param nms_threshold Intersection over Union threshold for overlapping rectangular detection area non-maximal suppression
	 * @param validCount
	 * @param model_in_w
	 * @param model_in_h
	 * @param scale_w
	 * @param scale_h
	 * @return The number of objects in detect_result_group
	 */
	static int process_arraysYOLO(int[] classIdArray, int[] indexArray, float[] filterBoxesArray, float[] objProbsArray, String[] labels, 
			detect_result_group group, float nms_threshold, int validCount, int model_in_w, int model_in_h, float scale_w, float scale_h) {
		//Set<Integer> classSet = new LinkedHashSet<Integer>();
		//Arrays.stream(classIdArray).boxed().sorted().forEach(e->classSet.add(e));
		int[] classSet = Arrays.copyOf(classIdArray, classIdArray.length);
		Arrays.sort(classSet);
		if(DEBUG_VERBOSE)
			System.out.println("Arrays loaded");
		for(Integer c: classSet) {
			if(DEBUG_VERBOSE)
				System.out.println("NMS for "+c);
			nmsYOLO(validCount, filterBoxesArray, classIdArray, indexArray, c, nms_threshold);
		}
		
		if(DEBUG) {
			System.out.println("NonMaximal suppression done indexArray:"+Arrays.toString(indexArray));
			System.out.println("NonMaximal suppression done classIdArray:"+Arrays.toString(classIdArray));
			System.out.println("NonMaximal suppression done filterBoxesArray:"+Arrays.toString(filterBoxesArray));
		}
	
		ArrayList<detect_result> groupArray = new ArrayList<detect_result>();
		/* box valid detect target */
		int i = 0;
		for(; i < validCount; i++) {
			if (indexArray[i] == -1) {
				continue;
			}
			int n = indexArray[i];
			if(DEBUG)
				System.out.println("IndexArray "+i+" n="+n);
			float x1       = filterBoxesArray[n * 4 + 0];
			float y1       = filterBoxesArray[n * 4 + 1];
			float x2       = x1 + filterBoxesArray[n * 4 + 2];
			float y2       = y1 + filterBoxesArray[n * 4 + 3];
			int   id       = classIdArray[n];
			float obj_conf = objProbsArray[i];
			if(DEBUG)
				System.out.println("x1="+x1+" y1="+y1+" x2="+x2+" y2="+y2+" id="+id+" obj_conf="+obj_conf);
			detect_result dr = new detect_result();
			dr.box.xmin   = (int)(clamp(x1, 0, model_in_w) / scale_w);
			dr.box.ymin    = (int)(clamp(y1, 0, model_in_h) / scale_h);
			dr.box.xmax  = (int)(clamp(x2, 0, model_in_w) / scale_w);
			dr.box.ymax = (int)(clamp(y2, 0, model_in_h) / scale_h);
			dr.probability       = obj_conf;
			dr.name		  = labels[id];
			groupArray.add(dr);
			
			if(DEBUG)
				System.out.printf("result %2d: (%4d, %4d, %4d, %4d), %s %s\n", i, dr.box.xmin,dr.box.ymin,dr.box.xmax,dr.box.ymax,dr.probability,dr.name);

		}
		//group.id
		group.count = groupArray.size();
		group.results = new detect_result[groupArray.size()];
		i = 0;
		for(; i < groupArray.size(); i++) {
			group.results[i] = groupArray.get(i);
		}
		return groupArray.size();
	}
	/**
	 * INT8 AFFINE<p/>
	 * Perform post processing on YOLOv5 type result sets from neural processing unit.<p/>
	 * Data that conforms to the parameters of YOLOv5, in a 3 layer structure if int8 output.
	 * @param bufs rknn_output[] output layers from NPU
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
	 * @return number of objects detected from detect_result_group populated with results array
	 */
	public static int post_process(rknn_output[] bufs, int model_in_h, int model_in_w, float conf_threshold,
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
		validCount0 = process(bufs[0].getBuf(), anchor0, grid_h0, grid_w0, model_in_h, model_in_w, stride0, filterBoxes0, objProbs0,
                   classId0, conf_threshold, qnt_zps.get(0), qnt_scales.get(0));

		// stride 16
		int stride1     = 16;
		int grid_h1     = model_in_h / stride1;
		int grid_w1     = model_in_w / stride1;
		int validCount1 = 0;
		if(bufs.length > 1)
			validCount1 = process(bufs[1].getBuf(), anchor1, grid_h1, grid_w1, model_in_h, model_in_w, stride1, filterBoxes1, objProbs1,
                   classId1, conf_threshold, qnt_zps.get(1), qnt_scales.get(1));

		// stride 32
		int stride2     = 32;
		int grid_h2     = model_in_h / stride2;
		int grid_w2     = model_in_w / stride2;
		int validCount2 = 0;
		if(bufs.length > 2)
			validCount2 = process(bufs[2].getBuf(), anchor2, grid_h2, grid_w2, model_in_h, model_in_w, stride2, filterBoxes2, objProbs2,
                   classId2, conf_threshold, qnt_zps.get(2), qnt_scales.get(2));

		int validCount = validCount0 + validCount1 + validCount2;
		if(DEBUG)
			System.out.printf("Valid count total =%d, 0=%d, 1=%d, 2=%d%n", validCount,validCount0,validCount1,validCount2);
		// no object detected
		if (validCount <= 0) {
			return 0;
		}

		int[] indexArray = new int[validCount];
		for (int i = 0; i < validCount; i++) {
			indexArray[i] = i;
		}
		float[] objProbsArray =  new float[validCount];
		int i = 0;
		for(int j = 0; j < objProbs0.size(); j++) {
			objProbsArray[i++] = objProbs0.get(j).floatValue();
		}
		for(int j = 0; j < objProbs1.size(); j++) {
			objProbsArray[i++] = objProbs1.get(j).floatValue();
		}
		for(int j = 0; j < objProbs2.size(); j++) {
			objProbsArray[i++] = objProbs2.get(j).floatValue();
		}
		if(DEBUG) {
			System.out.println("UnSorted ObjProbsArray:"+Arrays.toString(objProbsArray));
			System.out.println("UnSorted IndexArray:"+Arrays.toString(indexArray));
			System.out.println("Quicksort..");
		}
		quick_sort_indice_inverse(objProbsArray, 0, validCount - 1, indexArray);
		if(DEBUG) {
			System.out.println("Quicksort done");	
			System.out.println("Sorted ObjProbsArray:"+Arrays.toString(objProbsArray));
			System.out.println("Sorted IndexArray:"+Arrays.toString(indexArray));
		}
		
		float[] filterBoxesArray = new float[validCount*4];
		if(DEBUG)
			System.out.println("Filterboxes sizes 0="+filterBoxes0.size()+" 1="+filterBoxes1.size()+" 2="+filterBoxes2.size());
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
		if(DEBUG)
			System.out.println("ClassId sizes 0="+classId0.size()+" 1="+classId1.size()+" 2="+classId2.size());
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
		if(DEBUG) {
			System.out.println("raw ClassIdArray:"+Arrays.toString(classIdArray));
			System.out.println("raw filterBoxesArray:"+Arrays.toString(filterBoxesArray));
		}
		
		return process_arraysYOLO(classIdArray, indexArray, filterBoxesArray, objProbsArray, labels, 
				group, nms_threshold, validCount, model_in_w, model_in_h, scale_w, scale_h);

	}
	/**
	 * FLOAT<p/>
	 * Perform post processing on YOLOv5 type result sets from neural processing unit.<p/>
	 * Data that conforms to the parameters of YOLOv5, in a 3 layer structure if int8 output.
	 * @param bufs rknn_output[] output layers from NPU
	 * @param model_in_h Model input height
	 * @param model_in_w Model input width
	 * @param conf_threshold confidence threshold
	 * @param nms_threshold Non maximal suppresion threshold
	 * @param scale_w Scale width see above
	 * @param scale_h Scale height see above
	 * @param group Output structure initialized to accept output groups
	 * @param labels the Class Id labels acquired from whatever source
	 * @return number of objects detected from detect_result_group populated with results array
	 */
	public static int post_process(rknn_output[] bufs, int model_in_h, int model_in_w, float conf_threshold,
            float nms_threshold, float scale_w, float scale_h, detect_result_group group, String[] labels) {

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
		float[] fbuf0 = process(bufs[0].getBuf());
		validCount0 = process(fbuf0, anchor0, grid_h0, grid_w0, model_in_h, model_in_w, stride0, filterBoxes0, objProbs0,
                   classId0, conf_threshold);

		// stride 16
		int stride1     = 16;
		int grid_h1     = model_in_h / stride1;
		int grid_w1     = model_in_w / stride1;
		int validCount1 = 0;
		if(bufs.length > 1) {
			float[] fbuf1 = process(bufs[1].getBuf());
			validCount1 = process(fbuf1, anchor1, grid_h1, grid_w1, model_in_h, model_in_w, stride1, filterBoxes1, objProbs1,
                   classId1, conf_threshold);
		}

		// stride 32
		int stride2     = 32;
		int grid_h2     = model_in_h / stride2;
		int grid_w2     = model_in_w / stride2;
		int validCount2 = 0;
		if(bufs.length > 2) {
			float[] fbuf2 = process(bufs[2].getBuf());
			validCount2 = process(fbuf2, anchor2, grid_h2, grid_w2, model_in_h, model_in_w, stride2, filterBoxes2, objProbs2,
                   classId2, conf_threshold);
		}

		int validCount = validCount0 + validCount1 + validCount2;
		if(DEBUG)
			System.out.printf("Valid count total =%d, 0=%d, 1=%d, 2=%d%n", validCount,validCount0,validCount1,validCount2);
		// no object detected
		if (validCount <= 0) {
			return 0;
		}

		int[] indexArray = new int[validCount];
		for (int i = 0; i < validCount; i++) {
			indexArray[i] = i;
		}
		float[] objProbsArray =  new float[validCount];
		int i = 0;
		for(int j = 0; j < objProbs0.size(); j++) {
			objProbsArray[i++] = objProbs0.get(j).floatValue();
		}
		for(int j = 0; j < objProbs1.size(); j++) {
			objProbsArray[i++] = objProbs1.get(j).floatValue();
		}
		for(int j = 0; j < objProbs2.size(); j++) {
			objProbsArray[i++] = objProbs2.get(j).floatValue();
		}
		if(DEBUG) {
			System.out.println("UnSorted ObjProbsArray:"+Arrays.toString(objProbsArray));
			System.out.println("UnSorted IndexArray:"+Arrays.toString(indexArray));
			System.out.println("Quicksort..");
		}
		quick_sort_indice_inverse(objProbsArray, 0, validCount - 1, indexArray);
		if(DEBUG) {
			System.out.println("Quicksort done");	
			System.out.println("Sorted ObjProbsArray:"+Arrays.toString(objProbsArray));
			System.out.println("Sorted IndexArray:"+Arrays.toString(indexArray));
		}
		
		float[] filterBoxesArray = new float[validCount*4];
		if(DEBUG)
			System.out.println("Filterboxes sizes 0="+filterBoxes0.size()+" 1="+filterBoxes1.size()+" 2="+filterBoxes2.size());
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
		if(DEBUG)
			System.out.println("ClassId sizes 0="+classId0.size()+" 1="+classId1.size()+" 2="+classId2.size());
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
		if(DEBUG) {
			System.out.println("raw ClassIdArray:"+Arrays.toString(classIdArray));
			System.out.println("raw filterBoxesArray:"+Arrays.toString(filterBoxesArray));
		}
		
		return process_arraysYOLO(classIdArray, indexArray, filterBoxesArray, objProbsArray, labels, 
				group, nms_threshold, validCount, model_in_w, model_in_h, scale_w, scale_h);

	}
	/**
	 * Data that conforms to the parameters of a 2 layer structure of quantized INT8 output.<p/>
	 * When want_float flag is set to true on output layers, buffer will reflect floating point data
	 * @param input0 Layer 0 output from NPU, Contains 'predictions'
	 * @param input1 Layer 1 output from NPU Contains 'output_classes'
	 * @param box_priors Array loaded from external box_priors source.
	 * @param model_in_h Model input height
	 * @param model_in_w Model input width
	 * @param conf_threshold confidence threshold
	 * @param nms_threshold Non maximal suppresion threshold
	 * @param scale_w Scale width see above
	 * @param scale_h Scale height see above
	 * @param qnt_zps Zero point quantization for each layer
	 * @param qnt_scales Scale factors for quantization each layer
	 * @param group Output structure initialized to accept output groups
	 * @param labels the Class Id labels acquired from whatever source
	 * @return number of objects detected from detect_result_group populated with results array
	 */
	public static int post_process(byte[] input0, byte[] input1, float[][] box_priors, int model_in_h, int model_in_w,
            float nms_threshold, float scale_w, float scale_h, ArrayList<Integer> qnt_zps,
            ArrayList<Float> qnt_scales, detect_result_group group, String[] labels) {
	
		int validCount = 0;
		// convert input0 to 'predictions', convert input1 to 'output_classes'

		float[] predictionsArray =  process(input0, qnt_zps.get(0), qnt_scales.get(0));
		if(DEBUG) {
			System.out.println("Predictions array:"+predictionsArray.length);
			for(int i = 0; i < predictionsArray.length; i+=4) {
				System.out.printf("SSD postproc prediction %d xmin=%f ymin=%f xmax=%f ymax=%f%n", i,predictionsArray[i],
				predictionsArray[i+ 1],
				predictionsArray[i+ 2],
				predictionsArray[i+ 3]);
			}
		}
		float[] outputClassesArray =  process(input1, qnt_zps.get(1), qnt_scales.get(1));

		if(DEBUG) {
			System.out.println("Output classes array:"+outputClassesArray.length);
			  for (int i = 0; i < outputClassesArray.length; i++) {
				      System.out.printf("outputClasses Class#=%d outputClass=%f%n",i, outputClassesArray[i]);
			  }
		}
		
		int[][] output = new int[2][NUM_RESULTS];
		float[] props = new float[NUM_RESULTS];

		decodeCenterSizeBoxes(predictionsArray, box_priors);
		if(DEBUG) {
			for(int i = 0; i < predictionsArray.length; i+=4) {
				System.out.printf("prediction postdecodeCSB %d xmin=%f ymin=%f xmax=%f ymax=%f%n", i,predictionsArray[i * 4 + 0],
				predictionsArray[i * 4 + 1],
				predictionsArray[i * 4 + 2],
				predictionsArray[i * 4 + 3]);
			}
		}

		validCount = filterValidResult(outputClassesArray, output, NUM_CLASS, props);
		if(DEBUG)
			System.out.printf("Valid count total =%d", validCount);
		// no object detected
		if (validCount <= 0) {
			return 0;
		}
		if(DEBUG) {
			System.out.println("UnSorted output0:"+Arrays.toString(output[0]));
			System.out.println("UnSorted output1:"+Arrays.toString(output[1]));
			System.out.println("UnSorted props:"+Arrays.toString(props));
			System.out.println("sort..");
		}
		sort(output, props, validCount);
		if(DEBUG) {
			System.out.println("sort done");
			System.out.println("Sorted output0:"+Arrays.toString(output[0]));
			System.out.println("Sorted output1:"+Arrays.toString(output[1]));
			System.out.println("Sorted props:"+Arrays.toString(props));
		}
			
		return process_arraysSSD(output, predictionsArray, props, labels, group, nms_threshold, validCount, model_in_w, model_in_h, scale_w, scale_h);

	}
	/**
	 * FLOAT<p/>
	 * Perform post processing on InceptSSD type result sets from neural processing unit.<p/>
	 * Data that conforms to the parameters of InceptSSD, in a 2 layer structure of FLOAT output.<p/>
	 * When want_float flag is set to true on output layers, buffer will reflect floating point data
	 * @param input0 Layer 0 output from NPU, Contains 'predictions'
	 * @param input1 Layer 1 output from NPU Contains 'output_classes'
	 * @param box_priors Array loaded from external box_priors source.
	 * @param model_in_h Model input height
	 * @param model_in_w Model input width
	 * @param conf_threshold confidence threshold
	 * @param nms_threshold Non maximal suppresion threshold
	 * @param scale_w Scale width see above
	 * @param scale_h Scale height see above
	 * @param group Output structure initialized to accept output groups
	 * @param labels the Class Id labels acquired from whatever source
	 * @return number of objects detected from detect_result_group populated with results array
	 */
	public static int post_process(byte[] input0, byte[] input1, float[][] box_priors, int model_in_h, int model_in_w,
            float nms_threshold, float scale_w, float scale_h, detect_result_group group, String[] labels) {
	
		int validCount = 0;
		// convert input0 to 'predictions', convert input1 to 'output_classes'

		float[] predictionsArray =  process(input0);
		if(DEBUG) {
			for(int i = 0; i < predictionsArray.length; i+=4) {
				System.out.printf("prediction %d xmin=%f ymin=%f xmax=%f ymax=%f%n", i,predictionsArray[i * 4 + 0],
				predictionsArray[i * 4 + 1],
				predictionsArray[i * 4 + 2],
				predictionsArray[i * 4 + 3]);
			}
		}
		float[] outputClassesArray =  process(input1);
		if(DEBUG) {
			  for (int i = 0; i < outputClassesArray.length; i++) {
				    // Skip the first catch-all class.
				      System.out.printf("outputClasses Class#=%d outputClass=%f%n",i, outputClassesArray[i]);
			  }
		}
		
		int[][] output = new int[2][NUM_RESULTS];
		float[] props = new float[NUM_RESULTS];

		decodeCenterSizeBoxes(predictionsArray, box_priors);
		if(DEBUG) {
			for(int i = 0; i < predictionsArray.length; i+=4) {
				System.out.printf("prediction postdecodeCSB %d xmin=%f ymin=%f xmax=%f ymax=%f%n", i,predictionsArray[i * 4 + 0],
				predictionsArray[i * 4 + 1],
				predictionsArray[i * 4 + 2],
				predictionsArray[i * 4 + 3]);
			}
		}

		validCount = filterValidResult(outputClassesArray, output, NUM_CLASS, props);
		if(DEBUG)
			System.out.printf("Valid count total =%d", validCount);
		// no object detected
		if (validCount <= 0) {
			return 0;
		}
		if(DEBUG) {
			System.out.println("UnSorted output0:"+Arrays.toString(output[0]));
			System.out.println("UnSorted output1:"+Arrays.toString(output[1]));
			System.out.println("UnSorted props:"+Arrays.toString(props));
			System.out.println("sort..");
		}
		sort(output, props, validCount);
		if(DEBUG) {
			System.out.println("sort done");
			System.out.println("Sorted output0:"+Arrays.toString(output[0]));
			System.out.println("Sorted output1:"+Arrays.toString(output[1]));
			System.out.println("Sorted props:"+Arrays.toString(props));
		}
			
		return process_arraysSSD(output, predictionsArray, props, labels, group, nms_threshold, validCount, model_in_w, model_in_h, scale_w, scale_h);

	}

}
