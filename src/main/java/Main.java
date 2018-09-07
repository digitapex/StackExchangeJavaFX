import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.controlsfx.control.CheckListView;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.awt.*;
import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.List;

public class Main extends Application {
    private static List<Site> allFollowedSites = new ArrayList<>();
    private static List<String> followedSitesLinks = new ArrayList<>();
    private static final int RESULTS_PER_PAGE = 20;
    private static List<Button> buttonsInScene = new ArrayList<>();
    private static int lastQuestion = 0;
    private static List<Question> orderedQuestionsAllSites;
    private static Label currentQuestionsSpan = new Label();
    private static String theme = "light";
    private static Scene mainScene;
    private static VBox vBoxQuestions;
    private static Button buttonNext;
    private static boolean withAnswers;

    public static void main(String[] args) {
        launch(args);
    }

    private static Site parseSite(String stackSite) throws IOException {
        Document document = Jsoup.connect(stackSite).get();
        Site site = new Site();
        List<Question> questionList = new ArrayList<>();
        Elements questionElements = document.select(".summary h3");
        Elements numberOfAnswers = document.select(".status .mini-counts span");
        Elements linkAnswer = document.select(".summary h3 a");
        Elements siteFrom = document.select("title");
        for (int i = 0; i < questionElements.size(); i++) {
            Question question = new Question();
            question.setTitle(questionElements.get(i).text());
            question.setAnswersCount(numberOfAnswers.get(i).text());
            question.setQuestionLink(linkAnswer.get(i).attr("href"));
            question.setSiteName(siteFrom.get(0).text().replace(" Stack Exchange", ""));
            question.setSiteLink(stackSite);

            int answerCount = Integer.valueOf(numberOfAnswers.get(i).text());
            // if we want only questions with answers, but the question has 0 answers, skip
            if (withAnswers && answerCount == 0) {
                continue;
            }
            questionList.add(question);
        }
        site.setQuestions(questionList);
        // maybe not needed anymore
        site.setSiteName(stackSite);
        return site;
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        followedSitesLinks = new ArrayList<>();
        allFollowedSites = new ArrayList<>();
        buttonsInScene = new ArrayList<>();
        lastQuestion = 0;
        orderedQuestionsAllSites = new ArrayList<>();
        setTheme();
        withAnswers = Boolean.valueOf(getProperty("withAnswers", "false"));
        File file = new File("followedSites.txt");
        if (!file.exists()) {
            selectSites(primaryStage);
        } else {
            InputStream inputStream = new FileInputStream(file);
            ByteArrayOutputStream result = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) != -1) {
                result.write(buffer, 0, length);
            }
            String content = result.toString();
            String lines[] = content.split("\\r?\\n");
            Collections.addAll(followedSitesLinks, lines);

            BorderPane borderPane = new BorderPane();
            HBox hBox = new HBox();
            hBox.setSpacing(10);
            hBox.setPadding(new Insets(10, 10, 10, 10));
            hBox.setAlignment(Pos.CENTER_LEFT);
            Button button = new Button("Select Sites");
            button.setOnAction(e -> {
                try {
                    selectSites(primaryStage);
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
            });

            Button settings = new Button("Settings");
            settings.setOnAction(e -> {
                try {
                    settings(primaryStage);
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
            });

            Label pageNumLabel = new Label("Page 1");
            buttonNext = new Button("Next");
            buttonNext.setOnAction(e -> {
                int page = (lastQuestion / RESULTS_PER_PAGE) + 1;
                pageNumLabel.setText("Page " + page);
                int questionsSizeToShow = getQuestionsSizeToShow();
                if (questionsSizeToShow < RESULTS_PER_PAGE) {
                    buttonNext.setDisable(true);
                }
                vBoxQuestions.getChildren().clear();
                buttonsInScene = new ArrayList<>();
                for (int i = 0; i < questionsSizeToShow; i++) {

                    // recreate buttons because the last page could have less buttons
                    Button buttonQuestion = new Button();
                    buttonQuestion.setMaxWidth(Double.MAX_VALUE);
                    buttonQuestion.setAlignment(Pos.BASELINE_LEFT);
                    Question currentQuestion = orderedQuestionsAllSites.get(lastQuestion);
                    buttonQuestion.setText(currentQuestion.getSiteName() + ": " + currentQuestion.getTitle() + ", Answers: " + currentQuestion.getAnswersCount());
                    buttonQuestion.setOnAction(ev -> {
                        String fullLink = currentQuestion.getSiteLink() + currentQuestion.getQuestionLink();
                        openInBrowser(fullLink);
                    });
                    lastQuestion++;
                    buttonsInScene.add(buttonQuestion);
                    vBoxQuestions.getChildren().add(buttonQuestion);
                }
                enableResizingButtons();
                int firstQuestion = lastQuestion - questionsSizeToShow + 1;
                currentQuestionsSpan.setText(firstQuestion + "-" + lastQuestion + "/" + orderedQuestionsAllSites.size());
            });
            hBox.getChildren().addAll(button, settings, pageNumLabel, buttonNext, currentQuestionsSpan);
            borderPane.setTop(hBox);
            for (int j = 0; j < followedSitesLinks.size(); j++) {
                final int jCount = j;
                Thread thread = new Thread(() -> {
                    Site site = new Site();
                    try {
                        site = parseSite(followedSitesLinks.get(jCount));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    continueJavaFX(borderPane, site, primaryStage);
                });
                thread.start();
            }
        }

    }

    private static int getQuestionsSizeToShow() {
        if (orderedQuestionsAllSites.size() < RESULTS_PER_PAGE) {
            return orderedQuestionsAllSites.size();
        }
        int remainingQuestions = orderedQuestionsAllSites.size() - lastQuestion;
        int questionsToShow = RESULTS_PER_PAGE;
        if (remainingQuestions / RESULTS_PER_PAGE < 1) {
            questionsToShow = remainingQuestions;
        }
        return questionsToShow;
    }

    private void settings(Stage primaryStage) throws IOException {
        Stage stage = new Stage();
        VBox vBox = new VBox();
        vBox.setId("settingsVBox");
        RadioButton radioButtonLight = new RadioButton("Light Theme");
        RadioButton radioButtonDark = new RadioButton("Dark Theme");
        Properties prop = new Properties();
        File file = new File("settings.properties");
        if (file.exists()) {
            FileInputStream inputStream = new FileInputStream(file);
            prop.load(inputStream);
            theme = prop.getProperty("theme");
            if (theme.equals("light")) {
                radioButtonLight.setSelected(true);
            } else {
                radioButtonDark.setSelected(true);
            }
        } else {
            radioButtonLight.setSelected(true);
        }
        ToggleGroup toggleGroup = new ToggleGroup();
        radioButtonLight.setToggleGroup(toggleGroup);
        radioButtonDark.setToggleGroup(toggleGroup);

        Slider slider = new Slider();
        slider.setMin(1);
        slider.setMax(50);
        slider.setValue(Double.valueOf(getProperty("font", "20")));
        slider.setShowTickLabels(true);
        slider.setShowTickMarks(true);
        slider.setMajorTickUnit(10);
//        slider.setMinorTickCount(1);
//        slider.setBlockIncrement(1);
        slider.valueProperty().addListener((observable, oldValue, newValue) -> {
            mainScene.getRoot().setStyle("-fx-font-size: " + newValue + "px");

            //this is to prevent the font from having decimals when saving to properties
            slider.setValue(newValue.intValue());
        });
        Label labelFont = new Label("Adjust the font size if all " + System.lineSeparator() + RESULTS_PER_PAGE + " buttons don't fit the window height:");
        labelFont.setId("labelFont");
        CheckBox checkBoxAnswers = new CheckBox("Show only questions with at least 1 answer");
        checkBoxAnswers.setSelected(Boolean.valueOf(getProperty("withAnswers", "false")));
        boolean answersInitialValue = checkBoxAnswers.isSelected();
        vBox.getChildren().addAll(radioButtonLight, radioButtonDark, labelFont, slider, checkBoxAnswers);
        Scene settingsScene = new Scene(vBox, 600, 300);

        toggleGroup.selectedToggleProperty().addListener((observable, oldValue, newValue) -> {
            try {
                if (radioButtonLight.isSelected()) {
                    saveProperty("theme", "light");
                } else {
                    saveProperty("theme", "dark");
                }
                setTheme();
            } catch (IOException e) {
                e.printStackTrace();
            }

            if (theme.equals("light")) {
                mainScene.getStylesheets().clear();
                mainScene.getStylesheets().add("lightTheme.css");
                settingsScene.getStylesheets().clear();
                settingsScene.getStylesheets().add("lightTheme.css");
            } else {
                mainScene.getStylesheets().add("darkTheme.css");
                settingsScene.getStylesheets().add("darkTheme.css");
            }

        });
        if (theme.equals("light")) {
            settingsScene.getStylesheets().clear();
            settingsScene.getStylesheets().add("lightTheme.css");
        } else {
            settingsScene.getStylesheets().add("darkTheme.css");
        }
        stage.setScene(settingsScene);

        //prevents clicking in the main scene when this dialog open
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.show();

        stage.setOnCloseRequest(e -> {
            boolean answersEndValue = checkBoxAnswers.isSelected();
            try {
                String font = Integer.toString((int) slider.getValue());
                saveProperty("font", font);
                saveProperty("withAnswers", Boolean.toString(answersEndValue));

                // only reload main scene if this checkbox changed, otherwise not necessary
                if (answersInitialValue != answersEndValue) {
                    Platform.setImplicitExit(false);
                    start(primaryStage);
                }
            } catch (Exception e1) {
                e1.printStackTrace();
            }
        });
    }

    private static void continueJavaFX(BorderPane borderPane, Site site, Stage primaryStage) {
        allFollowedSites.add(site);

        // continue execution if all threads finished
        if (allFollowedSites.size() >= followedSitesLinks.size()) {
            orderedQuestionsAllSites = new ArrayList<>();
            // since threads return in unpredictable order, we sort sites so that questions are sorted by the site name
            allFollowedSites.sort(Comparator.comparing(Site::getSiteName));

            // determine which site has the most questions to use that maximum extent on all sites
            int mostQuestions = 0;
            for (Site allFollowedSite : allFollowedSites) {
                if (allFollowedSite.getQuestions().size() > mostQuestions) {
                    mostQuestions = allFollowedSite.getQuestions().size();
                }
            }
            for (int i = 0; i < mostQuestions; i++) {
                for (Site allFollowedSite : allFollowedSites) {
                    // but if the site has less elements than mostQuestions, skip to prevent IndexOutOfBounds in get(i)
                    if (i >= allFollowedSite.getQuestions().size()) {
                        continue;
                    }
                    orderedQuestionsAllSites.add(allFollowedSite.getQuestions().get(i));
                }
            }
            Platform.runLater(() -> {
                vBoxQuestions = new VBox();
                int questionsSizeToShow = getQuestionsSizeToShow();
                for (int i = 0; i < questionsSizeToShow; i++) {
                    Question currentQuestion = orderedQuestionsAllSites.get(i);
                    Button button1 = new Button(currentQuestion.getSiteName() + ": " + currentQuestion.getTitle() + ", Answers: " + currentQuestion.getAnswersCount());
                    button1.setMaxWidth(Double.MAX_VALUE);
                    button1.setAlignment(Pos.BASELINE_LEFT);
                    button1.setOnAction(e -> {
                        String fullLink = currentQuestion.getSiteLink() + currentQuestion.getQuestionLink();
                        openInBrowser(fullLink);
                    });
                    buttonsInScene.add(button1);
                    lastQuestion++;
                    vBoxQuestions.getChildren().add(button1);

                }
                borderPane.setCenter(vBoxQuestions);
                int firstQuestion = lastQuestion - questionsSizeToShow + 1;
                currentQuestionsSpan.setText(firstQuestion + "-" + lastQuestion + "/" + orderedQuestionsAllSites.size());
                if (questionsSizeToShow < RESULTS_PER_PAGE) {
                    buttonNext.setDisable(true);
                } else {
                    buttonNext.setDisable(false);
                }
                mainScene = primaryStage.getScene();
                if (mainScene == null) {
                    mainScene = new Scene(borderPane);
                } else {
                    mainScene.setRoot(borderPane);
                }

                if (theme.equals("light")) {
                    mainScene.getStylesheets().clear();
                    mainScene.getStylesheets().add("lightTheme.css");
                } else {
                    mainScene.getStylesheets().clear();
                    mainScene.getStylesheets().add("darkTheme.css");
                }

                try {
                    mainScene.getRoot().setStyle("-fx-font-size: " + getProperty("font", "20") + "px");
                } catch (IOException e) {
                    e.printStackTrace();
                }
                primaryStage.setScene(mainScene);
                primaryStage.setMaximized(true);
                primaryStage.show();

                primaryStage.setOnCloseRequest((ev) -> {
                    // needed on Windows to also exit in the command prompt
                    Platform.exit();
                });
                enableResizingButtons();

                try {
                    setTheme();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });

        }
    }

    private static void enableResizingButtons() {
        for (Button button : buttonsInScene) {
            button.setPrefHeight(vBoxQuestions.getHeight() / RESULTS_PER_PAGE);
        }
        ChangeListener<Number> vBoxSizeListener = ((observable, oldValue, newValue) -> {
            for (Button button : buttonsInScene) {
                button.setPrefHeight(vBoxQuestions.getHeight() / RESULTS_PER_PAGE);
            }
        });
        vBoxQuestions.widthProperty().addListener(vBoxSizeListener);
        vBoxQuestions.heightProperty().addListener(vBoxSizeListener);
    }


    private static void saveProperty(String key, String data) throws IOException {
        Properties properties = new Properties();
        File file = new File("settings.properties");
        if (file.exists()) {
            FileInputStream fileInputStream = new FileInputStream(file);
            properties.load(fileInputStream);
            fileInputStream.close();
        }
        FileOutputStream fileOutputStream = new FileOutputStream(file);
        properties.setProperty(key, data);
        properties.store(fileOutputStream, "");
        fileOutputStream.close();
    }

    private static String getProperty(String key, String defaultValue) throws IOException {
        Properties properties = new Properties();
        File file = new File("settings.properties");
        if (file.exists()) {
            FileInputStream fileInputStream = new FileInputStream(file);
            properties.load(fileInputStream);
            fileInputStream.close();
            return properties.getProperty(key, defaultValue);
        } else {
            return defaultValue;
        }
    }

    private static void setTheme() throws IOException {
        Properties prop = new Properties();
        File file = new File("settings.properties");
        if (file.exists()) {
            FileInputStream inputStream = new FileInputStream(file);
            prop.load(inputStream);
            theme = prop.getProperty("theme");
            inputStream.close();
        } else {
            theme = "light";
        }
    }

    private static void openInBrowser(String url) {
//        Runtime.getRuntime().exec(new String[]{"chromium-browser", url});

        if (Desktop.isDesktopSupported()) {

            // without a new thread it freezes on Ubuntu, https://stackoverflow.com/a/34429067/9702500
            new Thread(() -> {

                // this should be a cross platform solution, https://stackoverflow.com/a/18509384/9702500
                if (Desktop.isDesktopSupported()) {
                    Desktop desktop = Desktop.getDesktop();
                    try {
                        desktop.browse(new URI(url));
                    } catch (IOException | URISyntaxException e) {
                        e.printStackTrace();
                    }
                } else {
                    Runtime runtime = Runtime.getRuntime();
                    try {
                        runtime.exec("xdg-open " + url);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }).start();
        }


    }

    private void selectSites(Stage primaryStage) throws IOException {
        Stage stage = new Stage();
        final ObservableList<String> strings = FXCollections.observableArrayList();
        Document doc = Jsoup.connect("https://stackexchange.com/feeds/sites").get();
        Elements siteLinks = doc.select("id");
        for (Element element : siteLinks) {
            // first tag is just "http://stackexchange.com/feeds/sites", so skip
            if (element.text().equals("http://stackexchange.com/feeds/sites")) {
                continue;
            }
            strings.add(element.text());
        }

        final CheckListView<String> checkListView = new CheckListView<>(strings.sorted());
        checkListView.setBackground(new Background(new BackgroundFill(Color.WHITE, CornerRadii.EMPTY, Insets.EMPTY)));

        for (String followedSitesLink : followedSitesLinks) {
            checkListView.getCheckModel().check(followedSitesLink);
        }

        Scene selectSitesScene = new Scene(checkListView, 800, 600);
        if (theme.equals("light")) {
            selectSitesScene.getStylesheets().clear();
            selectSitesScene.getStylesheets().add("lightTheme.css");
        } else {
            selectSitesScene.getStylesheets().add("darkTheme.css");
        }
        stage.setScene(selectSitesScene);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.show();

        stage.setOnCloseRequest(we -> {
            ObservableList<String> checkedBoxes = checkListView.getCheckModel().getCheckedItems();
            File file = new File("followedSites.txt");
            boolean deleted = file.delete();
            if (deleted) {
                try {
                    file.createNewFile();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            checkedBoxes.forEach((tab) -> {
                try {
                    Files.write(Paths.get(file.toString()), (tab + "\r\n").getBytes(), StandardOpenOption.APPEND);
                } catch (IOException e) {
                    e.printStackTrace();
                }

            });

            try {

                // this is needed on the first run, otherwise Platform.runLater doesn't execute
                Platform.setImplicitExit(false);
                start(primaryStage);
            } catch (Exception e) {
                e.printStackTrace();
            }


        });

    }
}
