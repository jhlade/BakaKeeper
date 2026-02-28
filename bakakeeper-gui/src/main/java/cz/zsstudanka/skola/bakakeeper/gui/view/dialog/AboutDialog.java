package cz.zsstudanka.skola.bakakeeper.gui.view.dialog;

import cz.zsstudanka.skola.bakakeeper.gui.util.Icons;
import cz.zsstudanka.skola.bakakeeper.settings.Version;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

/**
 * Dialog "O programu" – zobrazuje logo, verzi, autora a licenci.
 */
public class AboutDialog extends Dialog<Void> {

    public AboutDialog(Window owner) {
        setTitle("O programu");
        initOwner(owner);

        getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        Version ver = Version.getInstance();

        VBox content = new VBox(12);
        content.setAlignment(Pos.CENTER);
        content.setPadding(new Insets(20));

        // logo
        Image logo = Icons.getIcon(128);
        if (logo != null) {
            ImageView logoView = new ImageView(logo);
            logoView.setFitWidth(96);
            logoView.setFitHeight(96);
            logoView.setPreserveRatio(true);
            content.getChildren().add(logoView);
        }

        // název
        Label nameLabel = new Label(ver.getName());
        nameLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        // verze
        Label versionLabel = new Label("Verze " + ver.getVersion());
        versionLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #6b7280;");

        // autor
        Label authorLabel = new Label(ver.getAuthor());
        authorLabel.setStyle("-fx-font-size: 12px;");

        // organizace
        Label orgLabel = new Label(ver.getOrganization());
        orgLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #6b7280;");

        // licence
        TextArea licenseArea = new TextArea(ver.getInfo(true));
        licenseArea.setEditable(false);
        licenseArea.setWrapText(true);
        licenseArea.setPrefHeight(160);
        licenseArea.setPrefWidth(400);
        licenseArea.setStyle("-fx-font-size: 11px;");

        content.getChildren().addAll(nameLabel, versionLabel, authorLabel, orgLabel,
                new Separator(), licenseArea);

        getDialogPane().setContent(content);
        getDialogPane().setPrefWidth(460);

        setResultConverter(bt -> null);
    }
}
