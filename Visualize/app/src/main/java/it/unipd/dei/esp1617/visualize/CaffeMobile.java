package it.unipd.dei.esp1617.visualize;

import java.nio.charset.StandardCharsets;

// Suppress these warnings since the JNI libraries have been built externally (using the following
// utility: https://github.com/sh1r0/caffe-android-lib), hence Android Studio is not able to resolve
// the different JNI methods.
 @SuppressWarnings("JniMissingFunction")


// Class representing the mobile version of the Caffe Framework.
class CaffeMobile {

	/**
	 * JNI method that loads the given model and the given weights.
	 *
	 * @param modelPath Path to the .prototxt file containing the model learned by the CNN.
	 * @param weightsPath Path to the .caffemodel file containing the weights learned by the CNN.
	 */
	public native void loadModel(String modelPath, String weightsPath);

	/**
	 * JNI method used to set the .binaryproto file containing the mean of the images of the dataset
	 * used to train the model.
	 *
	 * @param meanFile Path to the mean file.
	 */
	private native void setMeanWithMeanFile(String meanFile);

	/**
	 * JNI method used for predicting an image.
	 *
	 * @param data Array of bytes that can contain the byte array of the image or the byte array of
	 *             an encoded String containing the path to the image.
	 * @param width Width of the image (equal to 0 if data contains the path to the image).
	 * @param height Height of the image (equal to 0 if data contains the path to the image).
	 * @param k Top K predicted results to return
	 * @return Array of k integers corresponding to the resulting k predictions for the image.
	 */
	public native int[] predictImage(byte[] data, int width, int height, int k);


	// -------- Utility and wrapper methods ----------- //

	/**
	 * Encodes a string to the corresponding array of bytes.
	 * @param s String to be encoded.
	 * @return Byte array of the encoded string.
	 */
	private static byte[] stringToBytes(String s) {
		return s.getBytes(StandardCharsets.US_ASCII);
	}

	/**
	 * Wrapper method for the predictImage JNI method.
	 * @param imgPath The path to the image to be classified.
	 * @return	Same as the JNI method "predictImage".
	 */
	int[] predictImage(String imgPath) {
		return predictImage(stringToBytes(imgPath), 0, 0, 1);
	}

	/**
	 * Wrapper method for setting the mean for the model.
	 * @param meanFile The path to the .binaryproto file containing the mean.
	 */
	void setMean(String meanFile) {
		setMeanWithMeanFile(meanFile);
	}
}
