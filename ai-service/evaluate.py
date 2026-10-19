from __future__ import annotations

import argparse
import csv
import json
import time
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

#measures the trained model on photos it has never seen (run: python evaluate.py)
#it uses only the official Oxford-IIIT Pet "test" split (3,669 photos); train.py never touches these
#results are saved in evaluation/: summary.json, per_breed_metrics.csv and confusion_matrix.png


#2.input

#read the options typed after "python evaluate.py", or use the defaults
def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Evaluate the trained PawCare model on the held-out Oxford-IIIT Pet test split."
    )
    parser.add_argument("--model", type=Path, default=Path("models/pawcare_breed_classifier.keras"))
    parser.add_argument("--labels", type=Path, default=Path("models/labels.json"))
    parser.add_argument("--output", type=Path, default=Path("evaluation"))
    parser.add_argument("--batch-size", type=int, default=32)
    parser.add_argument("--timing-samples", type=int, default=50)    #how many single photos to time
    return parser.parse_args()


#read the 37 breed names; the file may hold {"labels": [...]} or just [...]
def load_labels(path: Path) -> list[str]:
    with path.open("r", encoding="utf-8") as file:
        payload = json.load(file)
    if isinstance(payload, dict):
        return payload["labels"]
    return payload


#3.process

def main() -> None:
    #2.input: the options, the trained model, its breed names and the test photos
    args = parse_args()
    import tensorflow as tf
    import tensorflow_datasets as tfds

    model = tf.keras.models.load_model(args.model)
    labels = load_labels(args.labels)

    test_raw, info = tfds.load("oxford_iiit_pet", split="test", as_supervised=True, with_info=True)
    if info.features["label"].names != labels:
        raise SystemExit("labels.json does not match the dataset label order. Evaluation stopped.")
    image_size = (224, 224)

    #get every photo ready exactly as train.py does
    def prepare(image, label):
        resized_image = tf.image.resize(image, image_size)
        prepared_image = tf.keras.applications.mobilenet_v2.preprocess_input(
            tf.cast(resized_image, tf.float32)
        )
        return prepared_image, label

    test_data = test_raw.map(prepare, num_parallel_calls=tf.data.AUTOTUNE)
    test_data = test_data.batch(args.batch_size)

    #3.process: predict every test photo, 32 at a time, and remember the right answer and the guess
    true_labels = []
    predicted_labels = []
    for images, batch_labels in test_data:
        probabilities = model.predict(images, verbose=0)
        for correct_breed in batch_labels.numpy().tolist():
            true_labels.append(correct_breed)
        #argmax: the position of the highest score = the guessed breed
        for guessed_breed in np.argmax(probabilities, axis=1).tolist():
            predicted_labels.append(guessed_breed)

    #time single photos, because the app sends one photo at a time
    average_seconds = time_single_predictions(model, test_raw.map(prepare), args.timing_samples)

    #work out the scores
    matrix = confusion_matrix(true_labels, predicted_labels, len(labels))
    per_breed = per_breed_metrics(matrix, labels)
    test_images = int(np.sum(matrix))
    correct = int(np.trace(matrix))             #trace: the diagonal, where guess = right answer
    accuracy = float(correct / test_images)
    f1_total = 0.0
    for row in per_breed:
        f1_total = f1_total + row["f1"]
    macro_f1 = float(f1_total / len(per_breed))  #macro F1: the average F1 over all 37 breeds

    #4.output: save the three result files and print the summary
    args.output.mkdir(parents=True, exist_ok=True)
    write_per_breed_csv(args.output / "per_breed_metrics.csv", per_breed)
    draw_confusion_matrix(args.output / "confusion_matrix.png", matrix, labels)
    summary = {
        "split": "oxford_iiit_pet test",
        "test_images": test_images,
        "accuracy": round(accuracy, 4),
        "macro_f1": round(macro_f1, 4),
        "average_inference_ms_per_image": round(average_seconds * 1000, 1),
        "timing_samples": args.timing_samples,
        "model": str(args.model),
    }
    with (args.output / "summary.json").open("w", encoding="utf-8") as file:
        json.dump(summary, file, indent=2)

    print_summary(summary, per_breed)


#average seconds to predict one photo; the first photo is a warm-up and is not counted
def time_single_predictions(model, dataset, samples: int) -> float:
    images = []
    for image, label in dataset.take(samples + 1):
        images.append(image)
    if len(images) < 2:
        return 0.0

    #image[None, ...] turns one photo into a batch of one photo
    model.predict(images[0][None, ...], verbose=0)

    start = time.perf_counter()
    for image in images[1:]:
        model.predict(image[None, ...], verbose=0)
    total_seconds = time.perf_counter() - start
    return total_seconds / (len(images) - 1)


#a table with one row per right breed and one column per guessed breed;
#each cell counts how often that right breed was guessed as that breed
def confusion_matrix(true_labels, predicted_labels, size: int) -> np.ndarray:
    matrix = np.zeros((size, size), dtype=np.int64)
    for position in range(len(true_labels)):
        right_breed = int(true_labels[position])
        guessed_breed = int(predicted_labels[position])
        matrix[right_breed, guessed_breed] = matrix[right_breed, guessed_breed] + 1
    return matrix


#precision: of the photos guessed as this breed, how many were right
#recall: of the photos of this breed, how many were found
#F1: one score that combines precision and recall
def per_breed_metrics(matrix: np.ndarray, labels: list[str]) -> list[dict]:
    rows = []
    for index in range(len(labels)):
        true_positive = matrix[index, index]
        predicted_total = matrix[:, index].sum()     #the column: everything guessed as this breed
        actual_total = matrix[index, :].sum()        #the row: every real photo of this breed

        precision = 0.0
        if predicted_total > 0:
            precision = true_positive / predicted_total
        recall = 0.0
        if actual_total > 0:
            recall = true_positive / actual_total
        f1 = 0.0
        if precision + recall > 0:
            f1 = 2 * precision * recall / (precision + recall)

        rows.append({
            "breed": labels[index],
            "precision": round(float(precision), 4),
            "recall": round(float(recall), 4),
            "f1": round(float(f1), 4),
            "support": int(actual_total),           #how many test photos this breed has
        })
    return rows


#4.output

#save one row per breed, for the report
def write_per_breed_csv(path: Path, rows: list[dict]) -> None:
    with path.open("w", encoding="utf-8", newline="") as file:
        writer = csv.DictWriter(file, fieldnames=["breed", "precision", "recall", "f1", "support"])
        writer.writeheader()
        writer.writerows(rows)


#draw the confusion matrix as a picture: a darker blue square means more photos in that cell
def draw_confusion_matrix(path: Path, matrix: np.ndarray, labels: list[str]) -> None:
    cell = 18                   #size of one square in pixels
    margin = 190                #room for the breed names
    size = len(labels)
    picture_size = margin + size * cell + 10
    image = Image.new("RGB", (picture_size, picture_size), "white")
    draw = ImageDraw.Draw(image)

    for row in range(size):
        #divide each row by its total so every breed is shown out of 100%, whatever its photo count
        row_total = matrix[row, :].sum()
        for column in range(size):
            share = 0.0
            if row_total > 0:
                share = matrix[row, column] / row_total
            shade = int(255 - share * 255)
            x = margin + column * cell
            y = margin + row * cell
            draw.rectangle([x, y, x + cell - 1, y + cell - 1], fill=(shade, shade, 255))

    #right breeds down the left side, guessed breeds across the top (turned sideways)
    for index in range(size):
        name = labels[index][:28]
        draw.text((4, margin + index * cell + 4), name, fill="black")
        column_label = Image.new("RGB", (margin - 10, cell), "white")
        ImageDraw.Draw(column_label).text((2, 4), name, fill="black")
        image.paste(column_label.rotate(90, expand=True), (margin + index * cell, 4))
    draw.text((4, 4), "rows: true\ncolumns: predicted", fill="black")
    image.save(path)


#print the main figures and the five breeds with the lowest F1
def print_summary(summary: dict, per_breed: list[dict]) -> None:
    print("\nPawCare breed classifier - held-out test split")
    print("-" * 46)
    print("Test images".ljust(32) + str(summary["test_images"]).rjust(14))
    print("Accuracy".ljust(32) + format(summary["accuracy"], ".4f").rjust(14))
    print("Macro F1".ljust(32) + format(summary["macro_f1"], ".4f").rjust(14))
    print("Avg inference (ms/image)".ljust(32) + format(summary["average_inference_ms_per_image"], ".1f").rjust(14))

    print("\nLowest F1 breeds:")
    weakest_first = sorted(per_breed, key=read_f1)
    for row in weakest_first[:5]:
        print("  " + row["breed"].ljust(30) + format(row["f1"], ".4f"))
    print("\nSaved to evaluation/: summary.json, per_breed_metrics.csv, confusion_matrix.png")


#used to sort the breeds by their F1 score
def read_f1(row: dict) -> float:
    return row["f1"]


#run main() only when this file is started directly (python evaluate.py)
if __name__ == "__main__":
    main()
