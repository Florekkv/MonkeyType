package gui.tak;

import javafx.animation.*;
import javafx.application.Application;
import javafx.beans.binding.Bindings;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.effect.Blend;
import javafx.scene.effect.BlendMode;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.Effect;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class MonkeyType extends Application {

    private static final String DICTIONARY_FOLDER = "src/main/java/Dictionary";
    private static final int WORDS_PER_PARAGRAPH = 30;
    private static final Integer[] AVAILABLE_TIMES = {15, 20, 45, 60, 90, 120, 300};


    private ObservableList<String> languages = FXCollections.observableArrayList();
    private StringProperty selectedLanguage = new SimpleStringProperty();
    private IntegerProperty selectedTime = new SimpleIntegerProperty();

    private List<String> words = new ArrayList<>();
    private int currentWordIndex = 0;

    private TextField inputField;
    private Text currentWordText;
    private Text paragraphText;
    private Text timeText;
    private Text wpmText;
    private Text averageWpmText;
    private Text accuracyText;
    private ProgressBar progressBar;

    private Instant startTime;
    private int totalWordsTyped;
    private int totalCorrectWordsTyped;
    private int totalExtraWordsTyped;
    private int totalMissedWords;
    private double totalKeystrokes;

    private Timeline timer;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        loadLanguages();

        ComboBox<String> languageComboBox = new ComboBox<>(languages);
        languageComboBox.setPromptText("Select language");
        languageComboBox.valueProperty().bindBidirectional(selectedLanguage);

        ComboBox<Integer> timeComboBox = new ComboBox<>(FXCollections.observableArrayList(AVAILABLE_TIMES));
        timeComboBox.setPromptText("Select time (seconds)");
        timeComboBox.valueProperty().bindBidirectional(selectedTime.asObject());

        Button startButton = new Button("Start");
        startButton.setOnAction(event -> startTest());

        HBox menuBar = new HBox(10);
        menuBar.setPadding(new Insets(10));
        menuBar.setAlignment(Pos.CENTER);
        menuBar.getChildren().addAll(languageComboBox, timeComboBox, startButton);

        inputField = new TextField();
        inputField.setPromptText("Start typing...");
        inputField.setDisable(true);
        inputField.setOnAction(event -> checkInput());

        currentWordText = new Text();
        currentWordText.setFont(Font.font(20));
        currentWordText.setFill(Color.GRAY);

        paragraphText = new Text();
        paragraphText.setFont(Font.font(16));
        paragraphText.setWrappingWidth(600);
        paragraphText.setFill(Color.BLACK);

        timeText = new Text();
        timeText.setFont(Font.font(16));
        timeText.setFill(Color.BLACK);

        wpmText = new Text();
        wpmText.setFont(Font.font(16));
        wpmText.setFill(Color.BLACK);

        averageWpmText = new Text();
        averageWpmText.setFont(Font.font(16));
        averageWpmText.setFill(Color.BLACK);

        accuracyText = new Text();
        accuracyText.setFont(Font.font(16));
        accuracyText.setFill(Color.BLACK);

        progressBar = new ProgressBar();
        progressBar.setPrefWidth(600);
        progressBar.setProgress(0);

        VBox root = new VBox(10);
        root.setPadding(new Insets(10));
        root.setAlignment(Pos.CENTER);
        root.getChildren().addAll(menuBar, currentWordText, paragraphText, inputField, timeText, wpmText, averageWpmText, accuracyText, progressBar);

        primaryStage.setScene(new Scene(root, 800, 600));
        primaryStage.setTitle("MonkeyType Game");
        primaryStage.show();
    }

    private void loadLanguages() {
        File folder = new File(DICTIONARY_FOLDER);
        File[] files = folder.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    String language = file.getName();
                    try {
                        List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
                        if (!lines.isEmpty()) {
                            languages.add(language);
                        }
                    } catch (IOException e) {
                        displayErrorAlert("Error reading file: " + file.getName());
                    }
                }
            }
        }
    }

    private void startTest() {
        if (selectedLanguage.get() == null || selectedTime.get() == 0) {
            displayErrorAlert("Please select a language and time.");
            return;
        }

        loadWords(selectedLanguage.get());
        if (words.isEmpty()) {
            displayErrorAlert("No words available for the selected language.");
            return;
        }

        inputField.setDisable(false);
        inputField.requestFocus();

        currentWordIndex = 0;
        updateCurrentWord();

        paragraphText.setText(generateParagraph());
        startTime = Instant.now();
        totalWordsTyped = 0;
        totalCorrectWordsTyped = 0;
        totalExtraWordsTyped = 0;
        totalMissedWords = 0;
        totalKeystrokes = 0;

        timer = new Timeline(new KeyFrame(Duration.seconds(1), event -> updateTimer()));
        timer.setCycleCount(selectedTime.get());
        timer.setOnFinished(event -> finishTest());
        timer.play();
    }

    private void loadWords(String language) {
        words.clear();
        File file = new File(DICTIONARY_FOLDER + "/" + language);
        try {
            List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
            words.addAll(lines);
        } catch (IOException e) {
            displayErrorAlert("Error reading file: " + file.getName());
        }
    }

    private void updateCurrentWord() {
        String word = words.get(currentWordIndex);
        currentWordText.setText(word);
        inputField.clear();

        StringBuilder styledWord = new StringBuilder();
        for (int i = 0; i < word.length(); i++) {
            styledWord.append(" ");
        }
        paragraphText.setText(paragraphText.getText().replaceFirst(" ", styledWord.toString()));

        HighlightTextAnimation animation = new HighlightTextAnimation(currentWordText);
        animation.play();
    }

    private String generateParagraph() {
        Random random = new Random();
        StringBuilder paragraph = new StringBuilder();
        for (int i = 0; i < WORDS_PER_PARAGRAPH; i++) {
            int randomIndex = random.nextInt(words.size());
            paragraph.append(words.get(randomIndex)).append(" ");
        }
        return paragraph.toString().trim();
    }

    private void checkInput() {
        String input = inputField.getText().trim();
        String currentWord = words.get(currentWordIndex);
        totalKeystrokes += input.length();

        if (input.equals(currentWord)) {
            totalWordsTyped++;
            totalCorrectWordsTyped++;
            currentWordText.setFill(Color.GREEN);
        } else if (currentWord.startsWith(input)) {
            totalWordsTyped++;
            currentWordText.setFill(Color.RED);
        } else {
            totalExtraWordsTyped++;
            currentWordText.setFill(Color.ORANGE);
        }

        if (!currentWord.endsWith(input)) {
            totalMissedWords++;
        }

        currentWordIndex++;
        if (currentWordIndex < words.size()) {
            updateCurrentWord();
        } else {
            finishTest();
        }
    }

    private void updateTimer() {
        long elapsedTime = (Instant.now().toEpochMilli() - startTime.toEpochMilli()) / 1000;
        long remainingTime = selectedTime.get() - elapsedTime;
        timeText.setText("Time: " + remainingTime + "s");

        int currentWpm = (int) (totalWordsTyped / ((double) elapsedTime / 60));
        wpmText.setText("Current WPM: " + currentWpm);

        double averageWpm = totalWordsTyped / ((double) elapsedTime / 60);
        averageWpmText.setText("Average WPM: " + averageWpm);

        double accuracy = (totalCorrectWordsTyped / (double) totalWordsTyped) * 100;
        accuracyText.setText("Accuracy: " + String.format("%.2f", accuracy) + "%");

        double progress = (double) elapsedTime / selectedTime.get();
        progressBar.setProgress(progress);
    }



    private void finishTest() {
        timer.stop();
        inputField.setDisable(true);
        inputField.clear();

        double totalWpm = totalWordsTyped / ((double) selectedTime.get() / 60);
        saveResultToFile(totalWpm);

        wpmText.setText("Total WPM: " + totalWpm);
        averageWpmText.setText("Average WPM: " + totalWpm);
        accuracyText.setText("Accuracy: " + calculateAccuracy() + "%");
    }

    private void saveResultToFile(double totalWpm) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
        String fileName = formatter.format(LocalDateTime.now()) + ".txt";

        try (FileWriter writer = new FileWriter(fileName)) {
            for (String word : words) {
                writer.write(word + " -> " + totalWpm + "wpm\n");
            }
        } catch (IOException e) {
            displayErrorAlert("Error saving results to file.");
        }
    }

    private double calculateAccuracy() {
        double totalTypedCharacters = totalWordsTyped + totalExtraWordsTyped;
        double accuracy = (totalTypedCharacters - totalKeystrokes) / totalTypedCharacters * 100;
        return Math.max(accuracy, 0);
    }

    private void displayErrorAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private static class HighlightTextAnimation extends Transition {
        private static final Color HIGHLIGHT_COLOR = Color.GREEN;
        private static final Duration DURATION = Duration.seconds(0.5);

        private final Text text;
        private final DropShadow dropShadow;
        private final Color originalColor;

        public HighlightTextAnimation(Text text) {
            this.text = text;
            this.dropShadow = new DropShadow();
            this.originalColor = (Color) text.getFill();
            this.dropShadow.setColor(HIGHLIGHT_COLOR);
            this.dropShadow.setRadius(5);
            this.dropShadow.setOffsetX(0);
            this.dropShadow.setOffsetY(0);
        }

        @Override
        protected void interpolate(double frac) {
            text.setFill(BlendColor(originalColor, HIGHLIGHT_COLOR, frac));
            text.setEffect(BlendEffect(null, dropShadow, frac));
        }

        private Color BlendColor(Color color1, Color color2, double ratio) {
            double r = color1.getRed() * (1 - ratio) + color2.getRed() * ratio;
            double g = color1.getGreen() * (1 - ratio) + color2.getGreen() * ratio;
            double b = color1.getBlue() * (1 - ratio) + color2.getBlue() * ratio;
            double a = color1.getOpacity() * (1 - ratio) + color2.getOpacity() * ratio;
            return new Color(r, g, b, a);
        }

        private Effect BlendEffect(Effect effect1, Effect effect2, double ratio) {
            Blend blend = new Blend();
            blend.setMode(BlendMode.ADD);
            blend.setTopInput(effect1);
            blend.setBottomInput(effect2);
            blend.setOpacity(ratio);
            return blend;
        }
    }
}
