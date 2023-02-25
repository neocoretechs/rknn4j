package com.neocoretechs.rknn4j.image;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

import javax.imageio.ImageIO;


/** 
 * This is the class for each image instance.
 * @author Jonathan Groff Copyright (C) NeoCoreTechs 2022
 */
public class Instance {
	// Store the bufferedImage.
	private BufferedImage image;
	private String label;
	private String name;
	private int width, height, channels;
	
	// Separate rgb channels.
	private int[][] red_channel, green_channel, blue_channel, gray_image;

	/** Constructs the Instance from a BufferedImage. */
	public Instance(String name, BufferedImage image, String label) {
		this.name = name;
		this.image = image;
		this.label = label;
		this.width = image.getWidth();
		this.height = image.getHeight();
		this.channels = computeChannels(image);

		// Get separate rgb channels.
		this.red_channel = new int[height][width];
		this.green_channel = new int[height][width];
		this.blue_channel = new int[height][width];

		for (int row = 0; row < this.height; ++row) {
			for (int col = 0; col < this.width; ++col) {
				Color c = new Color(this.image.getRGB(col, row));
				this.red_channel[row][col] = c.getRed();
				this.green_channel[row][col] = c.getGreen();
				this.blue_channel[row][col] = c.getBlue();
			}
		}
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

		// Get separate rgb channels.
		this.red_channel = new int[height][width];
		this.green_channel = new int[height][width];
		this.blue_channel = new int[height][width];
		this.image = new BufferedImage(fixWidth, fixHeight, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = this.image.createGraphics();
		while(!g.drawImage(image, 0, 0, fixWidth, fixHeight, null)) {
			try {
				System.out.println("Wait for image...");
				Thread.sleep(1);
			} catch (InterruptedException e) {}
		}
		g.dispose();
		this.channels = computeChannels(this.image);
		for (int row = 0; row < this.height; ++row) {
			for (int col = 0; col < this.width; ++col) {
				Color c = new Color(this.image.getRGB(col, row));
				this.red_channel[row][col] = c.getRed();
				this.green_channel[row][col] = c.getGreen();
				this.blue_channel[row][col] = c.getBlue();
			}
		}
	}
	/**
	 * RGB 888 left to right, top to bottom
	 * @return
	 */
	public byte[] getRGB888() {
		ByteBuffer bb = ByteBuffer.allocate(getWidth()*getHeight()*3);
		for (int row = 0; row < this.height; ++row) {
			for (int col = 0; col < this.width; ++col) {
				bb.put((byte)(this.red_channel[row][col] & 255));
				bb.put((byte)(this.green_channel[row][col] & 255));
				bb.put((byte)(this.blue_channel[row][col] & 255));
			}
		}
		return bb.array();
	}
	
	public static Instance readFile(String fileName, int fixWidth, int fixHeight) {
		File fi = new File(fileName);
		BufferedImage img = null;
		try {
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

	/** Construct the Instance from a 3D array. */
	public Instance(int[][][] image, String label) {
		this.label = label;
		this.height = image[0].length;
		this.width = image[0][0].length;
		
		this.red_channel = image[0];
		this.green_channel = image[1];
		this.blue_channel = image[2];
		if (image.length == 4) {
			this.gray_image = image[3];
		} else {
			this.gray_image = new int[this.height][this.width];
			for (int i = 0; i < this.height; i++) {
				for (int j = 0; j < this.width; j++) {
					this.gray_image[i][j] = (image[0][i][j] + image[1][i][j] + image[2][i][j]) / 3;
				}
			}
		}
	}

	public BufferedImage getImage() {
		return image;
	}
	
	public int getChannels() {
		return channels;
	}
	
	/** Gets separate red channel image. */
	public int[][] getRedChannel() {
		return red_channel;
	}

	/** Gets separate green channel image. */
	public int[][] getGreenChannel() {
		return green_channel;
	}

	/** Gets separate blue channel image. */
	public int[][] getBlueChannel() {
		return blue_channel;
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
	
	public static BufferedImage readBufferedImage(String fileName) {
		File fi = new File(fileName);
		BufferedImage img = null;
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
	
	public void drawDetections(detect_result_group detections) {
		if(detections == null || detections.results == null)
			return;
		Graphics graphics = image.getGraphics();
		for(detect_result dr: detections.results) {
			graphics.setColor(Color.CYAN);
			graphics.drawRect(dr.box.x, dr.box.y, dr.box.x+dr.box.width, dr.box.y+dr.box.height);
			graphics.setColor(Color.YELLOW);
			graphics.setFont(new Font("Arial Black", Font.BOLD, 20));
			graphics.drawString(dr.name+" "+((int)(dr.probability*100))+"%", dr.box.x, dr.box.y);
		}
		try {
			ImageIO.write(image, "jpg", new File("detections.jpg"));
		} catch (IOException e) {
			e.printStackTrace();
		}
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
}
