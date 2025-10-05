package com.neocoretechs.rknn4j.image;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferInt;
import java.awt.image.Raster;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

import javax.imageio.ImageIO;


/** 
 * This is the class for each image instance.
 * @author Jonathan Groff Copyright (C) NeoCoreTechs 2022
 */
public class Instance {
	private static boolean DEBUG = true;
	// Store the bufferedImage.
	private BufferedImage image;
	private String label;
	private String name;
	private int width, height, channels;
	
	private byte[] imageByteArray;
	
	private int[][] gray_image;

    enum PadMode { RESIZE, BITBLT_CENTER, BITBLT_CORNER }
    private PadMode mode = PadMode.BITBLT_CENTER;
    private int canvasSize = 640;
    private Color padColor = new Color(192,192,192); // Soft snow
    
	public Instance() {}
	
	/** Constructs the Instance from a BufferedImage. */
	public Instance(String name, BufferedImage image, String label) {
		this.name = name;
		this.image = image;
		this.label = label;
		this.width = image.getWidth();
		this.height = image.getHeight();
		this.channels = computeChannels(image);
		ByteBuffer bb = ByteBuffer.allocate(this.width*this.height*3);
		for (int row = 0; row < this.height; ++row) {
			for (int col = 0; col < this.width; ++col) {
				Color c = new Color(this.image.getRGB(col, row));
				bb.put((byte)c.getRed());
				bb.put((byte)c.getGreen());
				bb.put((byte)c.getBlue());
			}
		}
		this.imageByteArray = bb.array();
	}
	
	/**
	 * Constructs the Instance from a byte array.
	 * @param name Image file name
	 * @param width image width
	 * @param height image height
	 * @param channels image channels
	 * @param rawImage raw image bytes
	 * @param fixWidth target resize width
	 * @param fixHeight target resize height
	 * @param label image label option
	 */
	public Instance(String name, int width, int height, int channels, byte[] rawImage, int fixWidth, int fixHeight, String label) {
		this(name, width, height, channels, rawImage, fixWidth, fixHeight, label, false);
	}
	
	/**
	 * Constructs the Instance from a byte array. The boolean triggerSegmentation will be false if current image
	 * is within THRESHOLD of last image.
	 * @param name Image file name
	 * @param width image width
	 * @param height image height
	 * @param channels image channels
	 * @param rawImage raw image bytes
	 * @param fixWidth target resize width
	 * @param fixHeight target resize height
	 * @param label image label option
	 * @param rga true to use RockChip Graphics Accelerator; IMAGE MUST HAVE STRIDE 16, REQUIRES ROOT PROCESS
	 */
	public Instance(String name, int width, int height, int channels, byte[] rawImage, int fixWidth, int fixHeight, String label, boolean rga) {
		this.name = name;
		this.label = label;
		this.width = fixWidth;
		this.height = fixHeight;
		this.channels = channels;
		if(rga)
			this.imageByteArray = getRGARGB(rawImage, width, height, channels, fixWidth, fixHeight);
		else
			this.imageByteArray = getRGB(rawImage, width, height, channels, fixWidth, fixHeight);
		
		if(this.imageByteArray == null)
			throw new RuntimeException("Image resize error");
	
		this.image = new BufferedImage(fixWidth, fixHeight, BufferedImage.TYPE_INT_RGB);
	    final ByteBuffer bb = ByteBuffer.wrap(this.imageByteArray);//.order(ByteOrder.LITTLE_ENDIAN);
	    final int[] ret = new int[this.imageByteArray.length / 3];
	    int iret = 0;
		for (int row = 0; row < fixHeight; ++row) {
			for (int col = 0; col < fixWidth; ++col) {
				int r= Byte.toUnsignedInt(bb.get());
				int g= Byte.toUnsignedInt(bb.get());
				int b= Byte.toUnsignedInt(bb.get());
				//System.out.println("row="+row+" col="+col+" r="+r+" g="+g+" b="+b);
				Color c = new Color(r, g, b);
				ret[iret++] = c.getRGB();
			}
		}
	    this.image.setData(Raster.createRaster(this.image.getSampleModel(), new DataBufferInt(ret, ret.length), new Point() ) );
	}
	/** 
	 * Constructs the Instance from a BufferedImage after resizing
	 * 
	 */
	public Instance(String name, BufferedImage image, String label, int fixWidth, int fixHeight) {
		this.name = name;
		this.label = label;
		this.width = fixWidth;
		this.height = fixHeight;

		this.image = new BufferedImage(fixWidth, fixHeight, BufferedImage.TYPE_INT_RGB);
		Image i = image.getScaledInstance(fixWidth, fixHeight, Image.SCALE_AREA_AVERAGING);
		Graphics2D g = this.image.createGraphics();
		while(!g.drawImage(i, 0, 0, fixWidth, fixHeight, null)) {
			try {
				if(DEBUG)
					System.out.println("Wait for image...");
				Thread.sleep(1);
			} catch (InterruptedException e) {}
		}
		g.dispose();
		this.channels = computeChannels(this.image);
		ByteBuffer bb = ByteBuffer.allocate(this.width*this.height*3);
		for (int row = 0; row < this.height; ++row) {
			for (int col = 0; col < this.width; ++col) {
				Color c = new Color(this.image.getRGB(col, row));
				bb.put((byte)c.getRed());
				bb.put((byte)c.getGreen());
				bb.put((byte)c.getBlue());
			}
		}
		this.imageByteArray = bb.array();
	}
	/**
	 * Return converted RGB array bytes from previous image
	 * @return
	 */
	public byte[] getImageByteArray() {
		return imageByteArray;
	}
	/**
	 * Determine if translated image differs in threshold amount from provided image bytes
	 * @param previousImageByteArray The getImageByteArray of previous image
	 * @param threshold amount to change before returning true, typically float THRESHOLD = 0.03f;
	 * @return true if threshold change exceeded
	 */
	public boolean shouldSegment(byte[] previousImageByteArray, float threshold) {
		// determine if current frame has produced enough change to trigger new segmentation based on previous frame
		float changeRatio = threshold;
		if(previousImageByteArray == null)
			throw new RuntimeException("previous array null...");
		int changed = 0;
		for(int i = 0; i < imageByteArray.length; i++) {
			if ((imageByteArray[i] ^ previousImageByteArray[i]) != 0) changed++;
		}
		changeRatio = (float) changed / imageByteArray.length;
		if(DEBUG)
			System.out.println("change ratio="+changeRatio+" triggerSegmentation result="+(changeRatio > threshold));
		return (changeRatio > threshold);
	}
	/**
	 * RGB 888 left to right, top to bottom
	 * @return
	 */
	public byte[] getRGB888() {
		//ByteBuffer bb = ByteBuffer.allocate(getWidth()*getHeight()*3);
		//for (int row = 0; row < this.height; ++row) {
		//	for (int col = 0; col < this.width; ++col) {
		//		bb.put((byte)(this.red_channel[row][col] & 255));
		//		bb.put((byte)(this.green_channel[row][col] & 255));
		//		bb.put((byte)(this.blue_channel[row][col] & 255));
		//	}
		//}
		return imageByteArray;
	}
	
	public static Instance readFile(String fileName, int fixWidth, int fixHeight) {
		File fi = new File(fileName);
		BufferedImage img = null;
		try {
			if(DEBUG)
				System.out.println("Reading "+fi.getName());
			img = ImageIO.read(fi);
			String name = fi.getName();
			// Resize the image if requested.
			if (img.getHeight() != fixHeight || img.getWidth() != fixWidth) {
				return new Instance(name, img, name, fixWidth, fixHeight);
			}		
			return new Instance(name, img, name);

		} catch (IOException e) {
			System.err.println("Error: cannot load the image file");
		}
		return null;
	}
	
	public static Instance readFile(String fileName) {
		File fi = new File(fileName);
		BufferedImage img = null;
		try {
			if(DEBUG)
				System.out.println("Reading "+fi.getName());
			img = ImageIO.read(fi);
			String name = fi.getName();
			// Resize the image if requested. 
			return new Instance(name, img, name);
		} catch (IOException e) {
			System.err.println("Error: cannot load the image file");
		}
		return null;
	}

	public BufferedImage getImage() {
		return image;
	}
	
	public int getChannels() {
		return channels;
	}
	
	/** Gets the gray scale image. */
	public int[][] getGrayImage() {
		if(gray_image == null) {
			this.gray_image = new int[height][width];
			// Gray filter
			int[] dstBuff = new int[image.getWidth()*image.getHeight()];
			readLuminance(image, dstBuff);
			for (int row = 0; row < height; ++row) {
				for (int col = 0; col < width; ++col) {
					gray_image[row][col] = dstBuff[col + row * width];
				}
			}
		}
		return gray_image;
	}
	/**
	 * Luma represents the achromatic image while chroma represents the color component. 
	 * In video systems such as PAL, SECAM, and NTSC, a nonlinear luma component (Y') is calculated directly 
	 * from gamma-compressed primary intensities as a weighted sum, which, although not a perfect 
	 * representation of the colorimetric luminance, can be calculated more quickly without 
	 * the gamma expansion and compression used in photometric/colorimetric calculations. 
	 * In the Y'UV and Y'IQ models used by PAL and NTSC, the rec601 luma (Y') component is computed as
	 * Math.round(0.299f * r + 0.587f * g + 0.114f * b);
	 * rec601 Methods encode 525-line 60 Hz and 625-line 50 Hz signals, both with an active region covering 
	 * 720 luminance samples and 360 chrominance samples per line. The color encoding system is known as YCbCr 4:2:2.
	 * @param r
	 * @param g
	 * @param b
	 * @return Y'
	 */
	public static int luminance(float r, float g, float b) {
		return Math.round(0.299f * r + 0.587f * g + 0.114f * b);
	}
	
	/**
	 * Fill the data array with grayscale adjusted image data from sourceImage
	 */
	public static void readLuminance(BufferedImage sourceImage, int[] data) {
		int type = sourceImage.getType();
		if (type == BufferedImage.TYPE_CUSTOM || type == BufferedImage.TYPE_INT_RGB || type == BufferedImage.TYPE_INT_ARGB) {
			int[] pixels = (int[]) sourceImage.getData().getDataElements(0, 0, sourceImage.getWidth(), sourceImage.getHeight(), null);
			for (int i = 0; i < pixels.length; i++) {
				int p = pixels[i];
				int r = (p & 0xff0000) >> 16;
				int g = (p & 0xff00) >> 8;
				int b = p & 0xff;
				data[i] = luminance(r, g, b);
			}
		} else if (type == BufferedImage.TYPE_BYTE_GRAY) {
			byte[] pixels = (byte[]) sourceImage.getData().getDataElements(0, 0, sourceImage.getWidth(), sourceImage.getHeight(), null);
			for (int i = 0; i < pixels.length; i++) {
				data[i] = (pixels[i] & 0xff);
			}
		} else if (type == BufferedImage.TYPE_USHORT_GRAY) {
			short[] pixels = (short[]) sourceImage.getData().getDataElements(0, 0, sourceImage.getWidth(), sourceImage.getHeight(), null);
			for (int i = 0; i < pixels.length; i++) {
				data[i] = (pixels[i] & 0xffff) / 256;
			}
		} else if (type == BufferedImage.TYPE_3BYTE_BGR) {
            byte[] pixels = (byte[]) sourceImage.getData().getDataElements(0, 0, sourceImage.getWidth(), sourceImage.getHeight(), null);
            int offset = 0;
            int index = 0;
            for (int i = 0; i < pixels.length; i+=3) {
                int b = pixels[offset++] & 0xff;
                int g = pixels[offset++] & 0xff;
                int r = pixels[offset++] & 0xff;
                data[index++] = luminance(r, g, b);
            }
        } else {
			throw new IllegalArgumentException("Unsupported image type: " + type);
		}
	}
	
	/**
	 * Fill the data array with grayscale adjusted image data from sourceImage
	 */
	public static byte[] readRGB(BufferedImage sourceImage) {
		ByteBuffer bb = ByteBuffer.allocate(sourceImage.getWidth()*sourceImage.getHeight()*3);
		int type = sourceImage.getType();
		if (type == BufferedImage.TYPE_CUSTOM || type == BufferedImage.TYPE_INT_RGB || type == BufferedImage.TYPE_INT_ARGB) {
			int[] pixels = (int[]) sourceImage.getData().getDataElements(0, 0, sourceImage.getWidth(), sourceImage.getHeight(), null);
			for (int i = 0; i < pixels.length; i++) {
				int p = pixels[i];
				int r = (p & 0xff0000) >> 16;
				int g = (p & 0xff00) >> 8;
				int b = p & 0xff;
				bb.put((byte)r);
				bb.put((byte)g);
				bb.put((byte)b);
			}
		} else if (type == BufferedImage.TYPE_BYTE_GRAY) {
			byte[] pixels = (byte[]) sourceImage.getData().getDataElements(0, 0, sourceImage.getWidth(), sourceImage.getHeight(), null);
			for (int i = 0; i < pixels.length; i++) {
				int rgb = (pixels[i] & 0xff);
				bb.put((byte)rgb);
				bb.put((byte)rgb);
				bb.put((byte)rgb);
			}
		} else if (type == BufferedImage.TYPE_USHORT_GRAY) {
			short[] pixels = (short[]) sourceImage.getData().getDataElements(0, 0, sourceImage.getWidth(), sourceImage.getHeight(), null);
			for (int i = 0; i < pixels.length; i++) {
				int rgb = (pixels[i] & 0xffff) / 256;
				bb.put((byte)rgb);
				bb.put((byte)rgb);
				bb.put((byte)rgb);
			}
		} else if (type == BufferedImage.TYPE_3BYTE_BGR) {
            byte[] pixels = (byte[]) sourceImage.getData().getDataElements(0, 0, sourceImage.getWidth(), sourceImage.getHeight(), null);
            int offset = 0;
            int index = 0;
            for (int i = 0; i < pixels.length; i+=3) {
                int b = pixels[offset++] & 0xff;
                int g = pixels[offset++] & 0xff;
                int r = pixels[offset++] & 0xff;
    			bb.put((byte)r);
				bb.put((byte)g);
				bb.put((byte)b);
            }
        } else {
			throw new IllegalArgumentException("Unsupported image type: " + type);
		}
		return bb.array();
	}

	public static BufferedImage readBufferedImage(String fileName) {
		File fi = new File(fileName);
		BufferedImage img = null;
		if(DEBUG)
			System.out.println("Reading "+fi.getName());
		try {
				img = ImageIO.read(fi);
		} catch (IOException e) {
				e.printStackTrace();
				return null;
		}
		return img;
	}
	
	public static int computeChannels(BufferedImage sourceImage) {
		switch( sourceImage.getType() ) {
			case BufferedImage.TYPE_CUSTOM:
			case BufferedImage.TYPE_INT_RGB:
			case BufferedImage.TYPE_3BYTE_BGR:
				return 3;
			case BufferedImage.TYPE_INT_ARGB:
				return 4;
			case BufferedImage.TYPE_BYTE_GRAY:
			case BufferedImage.TYPE_USHORT_GRAY:
				return 1;
			default:
				throw new IllegalArgumentException("Unsupported image type: " + sourceImage.getType());
		}
	}
	
	public static int[] computeDimensions(BufferedImage sourceImage) {
		int[] dims = new int[2];
		dims[0] = sourceImage.getWidth();
		dims[1] = sourceImage.getHeight();
		return dims;
	}
	/**
	 * Draw the detection boxes and probabilities and save to file detections.jpg
	 * @param detections
	 */
	public void saveDetections(detect_result_group detections, String fileName) {
		if(detections == null || detections.results == null)
			return;
		Graphics graphics = image.getGraphics();
		for(detect_result dr: detections.results) {
			graphics.setColor(Color.CYAN);
			graphics.drawRect(dr.box.xmin, dr.box.ymin, dr.box.xmax-dr.box.xmin, dr.box.ymax-dr.box.ymin);
			graphics.setColor(Color.YELLOW);
			graphics.setFont(new Font("Courier", Font.PLAIN, 10));
			graphics.drawString(dr.name+" "+((int)(dr.probability*100))+"%", dr.box.xmin, dr.box.ymin);
		}
		try {
			ImageIO.write(image, "jpg", new File(fileName+".jpg"));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	public BufferedImage drawDetections(detect_result_group detections) {
		if(detections == null || detections.results == null)
			return null;
		Graphics graphics = image.getGraphics();
		for(detect_result dr: detections.results) {
			graphics.setColor(Color.CYAN);
			graphics.drawRect(dr.box.xmin, dr.box.ymin, dr.box.xmax-dr.box.xmin, dr.box.ymax-dr.box.ymin);
			graphics.setColor(Color.YELLOW);
			graphics.setFont(new Font("Courier", Font.PLAIN, 10));
			graphics.drawString(dr.name+" "+((int)(dr.probability*100))+"%", dr.box.xmin, dr.box.ymin);
		}
		return image;
	}
	/**
	 * Draw the detection boxes and probabilities to passed image and save to detections.jpg
	 * @param bimage
	 * @param detections
	 */
	public static void saveDetections(BufferedImage bimage, detect_result_group detections) {
		if(detections == null || detections.results == null)
			return;
		Graphics graphics = bimage.getGraphics();
		for(detect_result dr: detections.results) {
			graphics.setColor(Color.CYAN);
			graphics.drawRect(dr.box.xmin, dr.box.ymin, dr.box.xmax-dr.box.xmin, dr.box.ymax-dr.box.ymin);
			graphics.setColor(Color.YELLOW);
			graphics.setFont(new Font("Courier", Font.PLAIN, 10));
			graphics.drawString(dr.name+" "+((int)(dr.probability*100))+"%", dr.box.xmin, dr.box.ymin);
		}
		try {
			ImageIO.write(bimage, "jpg", new File("detections.jpg"));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	/**
	 * Draw the detection boxes and probabilities to image instance and return as JPEG byte array
	 * @param detections
	 * @return
	 */
	public byte[] detectionsToJPEGBytes(detect_result_group detections) {
		if(detections == null || detections.results == null)
			return null;
		Graphics graphics = image.getGraphics();
		for(detect_result dr: detections.results) {
			graphics.setColor(Color.CYAN);
			graphics.drawRect(dr.box.xmin, dr.box.ymin, dr.box.xmax-dr.box.xmin, dr.box.ymax-dr.box.ymin);
			graphics.setColor(Color.YELLOW);
			graphics.setFont(new Font("Courier", Font.PLAIN, 10));
			graphics.drawString(dr.name+" "+((int)(dr.probability*100))+"%", dr.box.xmin, dr.box.ymin);
		}
		ByteArrayOutputStream bos = null;
		byte[] r = null;
		try {
			bos = new ByteArrayOutputStream();
			ImageIO.write(image, "jpg", bos);
			r = bos.toByteArray();
			bos.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return r;
	}
	
	public static byte[] resizeRawJPEG(byte[] rawImage, int width, int height, int channels, int fixWidth, int fixHeight) {
		Instance ins = new Instance("tmp",width, height, channels, rawImage, fixWidth, fixHeight,"tmp");
		ByteArrayOutputStream bos = null;
		byte[] r = null;
		try {
			bos = new ByteArrayOutputStream();
			ImageIO.write(ins.image, "jpg", bos);
			r = bos.toByteArray();
			bos.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return r;
	}
	
	public String getName() {
		return name;
	}
	
	/** Gets the image width. */
	public int getWidth() {
		return width;
	}

	/** Gets the image height. */
	public int getHeight() {
		return height;
	}

	/** Gets the image label. */
	public String getLabel() {
		return label;
	}
	
	public String toString() {
		return image.toString()+" "+label;
	}
	
    public BufferedImage process(BufferedImage input, PadMode selectedMode) {
        this.mode = selectedMode;
        switch (mode) {
            case RESIZE: return resizeImage(input);
            case BITBLT_CENTER: return bitbltImage(input, true);
            case BITBLT_CORNER: return bitbltImage(input, false);
            default: throw new IllegalArgumentException("Invalid mode");
        }
    }

    private BufferedImage resizeImage(BufferedImage input) {
        BufferedImage resized = new BufferedImage(canvasSize, canvasSize, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = resized.createGraphics();
        g.drawImage(input, 0, 0, canvasSize, canvasSize, null);
        g.dispose();
        return resized;
    }

    private BufferedImage bitbltImage(BufferedImage input, boolean center) {
        BufferedImage padded = new BufferedImage(canvasSize, canvasSize, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = padded.createGraphics();
        g.setColor(padColor);
        g.fillRect(0, 0, canvasSize, canvasSize);
        int x = center ? (canvasSize - input.getWidth()) / 2 : 0;
        int y = center ? (canvasSize - input.getHeight()) / 2 : 0;
        g.drawImage(input, x, y, null);
        g.dispose();
        return padded;
    }

	public native byte[] getRGB(byte[] imageBytes, int img_height, int img_width, int channel, int height, int width);
	public native byte[] getRGARGB(byte[] imageBytes, int img_height, int img_width, int channel, int height, int width);
	//public native byte[] getCapture(int cam);

}
