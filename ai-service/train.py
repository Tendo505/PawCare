from __future__ import annotations

import argparse
import json
from pathlib import Path

#trains the PawCare breed model (run: python train.py --epochs 8)
#idea: "transfer learning" - MobileNetV2 already learned to see shapes and fur on 1.4 million ImageNet
#photos; we keep that part fixed and only train a small new last layer that picks one of 37 breeds
#data: the Oxford-IIIT Pet dataset (37 cat and dog breeds), downloaded by tensorflow-datasets
#result: models/pawcare_breed_classifier.keras and models/labels.json, used by app/classifier.py


#2.input

#read the options typed after "python train.py", or use the defaults
def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Train PawCare on the Oxford-IIIT Pet dataset.")
    parser.add_argument("--epochs", type=int, default=8)           #how many times to go through all photos
    parser.add_argument("--batch-size", type=int, default=32)      #how many photos to learn from at once
    parser.add_argument(
        "--output",
        type=Path,
        default=Path("models/pawcare_breed_classifier.keras"),
    )
    return parser.parse_args()


#3.process

def main() -> None:
    #2.input: the options, then the dataset
    args = parse_args()
    import tensorflow as tf
    import tensorflow_datasets as tfds

    #85% of the "train" photos are used for learning and 15% for checking during training
    #the separate "test" photos are never used here; evaluate.py uses them to measure accuracy
    (train_raw, validation_raw), info = tfds.load(
        "oxford_iiit_pet",
        split=["train[:85%]", "train[85%:]"],
        as_supervised=True,
        with_info=True,
    )
    labels = info.features["label"].names       #the 37 breed names, in the model's output order
    image_size = (224, 224)                     #MobileNetV2 expects 224 x 224 pixel photos

    #3.process: get every photo ready for the model
    def prepare(image, label):
        #resize to 224 x 224, then scale the pixels the way MobileNetV2 expects
        resized_image = tf.image.resize(image, image_size)
        prepared_image = tf.keras.applications.mobilenet_v2.preprocess_input(
            tf.cast(resized_image, tf.float32)
        )
        return prepared_image, label

    #training photos are shuffled so the model does not learn their order; batch = 32 photos at a time
    training_data = train_raw.map(prepare, num_parallel_calls=tf.data.AUTOTUNE)
    training_data = training_data.shuffle(1500)
    training_data = training_data.batch(args.batch_size)
    training_data = training_data.prefetch(tf.data.AUTOTUNE)

    validation_data = validation_raw.map(prepare, num_parallel_calls=tf.data.AUTOTUNE)
    validation_data = validation_data.batch(args.batch_size)
    validation_data = validation_data.prefetch(tf.data.AUTOTUNE)

    #small random changes (flip, rotate, zoom, contrast) so the model sees more varied photos
    augmentation = tf.keras.Sequential(
        [
            tf.keras.layers.RandomFlip("horizontal"),
            tf.keras.layers.RandomRotation(0.08),
            tf.keras.layers.RandomZoom(0.1),
            tf.keras.layers.RandomContrast(0.1),
        ]
    )

    #the pretrained MobileNetV2, without its own last layer; trainable = False keeps what it already learned
    base_model = tf.keras.applications.MobileNetV2(
        input_shape=(224, 224, 3),              #width, height, 3 colours (red, green, blue)
        include_top=False,
        weights="imagenet",
    )
    base_model.trainable = False

    #build the model step by step: photo -> augmentation -> MobileNetV2 -> average -> dropout -> 37 breed scores
    inputs = tf.keras.Input(shape=(224, 224, 3))
    features = augmentation(inputs)
    features = base_model(features, training=False)
    features = tf.keras.layers.GlobalAveragePooling2D()(features)
    features = tf.keras.layers.Dropout(0.25)(features)       #randomly ignore 25% to avoid memorising
    outputs = tf.keras.layers.Dense(len(labels), activation="softmax")(features)  #softmax: scores add up to 1
    model = tf.keras.Model(inputs, outputs)

    #Adam adjusts the weights; the loss measures how wrong a guess is; accuracy is shown after each epoch
    model.compile(
        optimizer=tf.keras.optimizers.Adam(1e-3),
        loss="sparse_categorical_crossentropy",
        metrics=["accuracy"],
    )

    #stop early when the checking score stops improving for 3 epochs, and keep only the best model
    args.output.parent.mkdir(parents=True, exist_ok=True)
    callbacks = [
        tf.keras.callbacks.EarlyStopping(patience=3, restore_best_weights=True),
        tf.keras.callbacks.ModelCheckpoint(args.output, save_best_only=True),
    ]

    #train
    model.fit(
        training_data,
        validation_data=validation_data,
        epochs=args.epochs,
        callbacks=callbacks,
    )

    #4.output: save the breed names in the same order as the model's 37 outputs
    labels_path = args.output.parent / "labels.json"
    with labels_path.open("w", encoding="utf-8") as file:
        json.dump(
            {"labels": labels, "dataset": "oxford_iiit_pet", "image_size": [224, 224]},
            file,
            indent=2,
        )
    print("Saved model to " + str(args.output) + " and " + str(len(labels)) + " labels to " + str(labels_path))


#run main() only when this file is started directly (python train.py)
if __name__ == "__main__":
    main()
