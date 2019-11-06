package com.github.jargors;
import com.github.jargors.DesktopController;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import java.io.IOException;

public class Desktop extends Application {
  public void start(Stage stage) throws IOException {
    FXMLLoader fxmll = new FXMLLoader(Desktop.class.getResource("/fxml/Desktop.fxml"));
    Scene scene = new Scene(fxmll.load());
    DesktopController dc = fxmll.getController();
    dc.setStage(stage);
    scene.widthProperty().addListener((a, b, c) -> {
      dc.setWindowWidth((double) c);
    });
    scene.heightProperty().addListener((a, b, c) -> {
      dc.setWindowHeight((double) c);
    });
    stage.setTitle("Jargo Desktop");
    stage.setScene(scene);
    stage.show();
  }
}

