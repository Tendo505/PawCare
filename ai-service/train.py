from __future__ import annotations

import argparse
import json
from pathlib import Path


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Train PawCare on the Oxford-IIIT Pet dataset.")
    parser.add_argument("--epochs", type=int, default=8)
    parser.add_argument("--batch-size", type=int, default=32)
    parser.add_argument("--output", type=Path, default=Path("models/pawcare_breed_classifier.keras"))
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    import tensorflow as tf
    import tensorflow_datasets as tfds

    (train_raw, validation_raw), info = tfds.load(
        "oxford_iiit_pet",
        split=["train[:85%]", "train[85%:]"],
        as_supervised=True,
        with_info=True,
    )
    labels = info.features["label"].names
    image_size = (224, 224)

    def prepare(image, label):
        image = tf.image.resize(image, image_size)
        image = tf.keras.applications.mobilenet_v2.preprocess_input(tf.cast(image, tf.float32))
        return image, label

    train = train_raw.map(prepare, num_parallel_calls=tf.data.AUTOTUNE).shuffle(1500).batch(args.batch_size).prefetch(tf.data.AUTOTUNE)
    validation = validation_raw.map(prepare, num_parallel_calls=tf.data.AUTOTUNE).batch(args.batch_size).prefetch(tf.data.AUTOTUNE)

    augmentation = tf.keras.Sequential([
        tf.keras.layers.RandomFlip("horizontal"),
        tf.keras.layers.RandomRotation(0.08),
        tf.keras.layers.RandomZoom(0.1),
        tf.keras.layers.RandomContrast(0.1),
    ])
    base = tf.keras.applications.MobileNetV2(input_shape=(*image_size, 3), include_top=False, weights="imagenet")
    base.trainable = False
    inputs = tf.keras.Input(shape=(*image_size, 3))
    x = augmentation(inputs)
    x = base(x, training=False)
    x = tf.keras.layers.GlobalAveragePooling2D()(x)
    x = tf.keras.layers.Dropout(0.25)(x)
    outputs = tf.keras.layers.Dense(len(labels), activation="softmax")(x)
    model = tf.keras.Model(inputs, outputs)
    model.compile(optimizer=tf.keras.optimizers.Adam(1e-3), loss="sparse_categorical_crossentropy", metrics=["accuracy"])

    args.output.parent.mkdir(parents=True, exist_ok=True)
    callbacks = [
        tf.keras.callbacks.EarlyStopping(patience=3, restore_best_weights=True),
        tf.keras.callbacks.ModelCheckpoint(args.output, save_best_only=True),
    ]
    model.fit(train, validation_data=validation, epochs=args.epochs, callbacks=callbacks)
    with (args.output.parent / "labels.json").open("w", encoding="utf-8") as file:
        json.dump({"labels": labels, "dataset": "oxford_iiit_pet", "image_size": list(image_size)}, file, indent=2)
    print(f"Saved model to {args.output} and {len(labels)} labels to {args.output.parent / 'labels.json'}")


if __name__ == "__main__":
    main()
