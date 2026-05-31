import os
import cv2

def read_local_image():
    base_dir = os.path.dirname(os.path.abspath(__file__))
    image_path = os.path.join(base_dir, "test_image.jpg")

    img = cv2.imread(image_path)

    if img is None:
        raise FileNotFoundError(f"Cannot read image: {image_path}")

    success, buffer = cv2.imencode(".png", img)

    if not success:
        raise RuntimeError("Failed to encode image")

    return buffer.tobytes()
