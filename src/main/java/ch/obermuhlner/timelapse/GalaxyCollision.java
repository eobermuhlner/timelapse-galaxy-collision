package ch.obermuhlner.timelapse;

import javafx.application.Application;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import javax.imageio.ImageIO;
import java.awt.image.RenderedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

public class GalaxyCollision extends Application {
    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        Group root = new Group();
        Scene scene = new Scene(root);

        double width = 800;
        double height = 600;

        Canvas canvas = new Canvas(width, height);
        root.getChildren().add(canvas);

        primaryStage.setScene(scene);
        primaryStage.show();

        renderSkyImages(canvas);
    }

    private void renderSkyImages(Canvas canvas) {
        double width = canvas.getWidth();
        double height = canvas.getHeight();

        int nStars1 = 5000;
        int nStars2 = 5000;
        int nFrames = 1500;
        int startCollisionFrame = 1000;

        Random random = new Random();
        List<Star> stars = new ArrayList<>();
        for (int i = 0; i < nStars1; i++) {
            stars.add(createStar(random, star -> {
                star.x = random.nextDouble() * width;
                star.y = random.nextDouble() * height;
            }));
        }

        double galaxyRadius = Math.min(width, height);
        for (int i = 0; i < nStars2; i++) {
            stars.add(createStar(random, star -> {
                do {
                    star.x = random.nextGaussian() * galaxyRadius;
                } while(star.x < -width);
                do {
                    star.y = random.nextGaussian() * galaxyRadius;
                } while(star.y < -height);
                star.x += 2 * width;
                star.y += -height;
                star.deltaX = -1.2;
                star.deltaY = 1.1;
            }));
        }

        Star collidingStar = new Star();
        collidingStar.x = width / 2;
        collidingStar.y = height / 2;
        collidingStar.color = Color.WHITE;
        collidingStar.radius = 0.5;
        List<Star> specialStars = Arrays.asList(collidingStar);

        for (int i = 0; i < nFrames; i++) {
            System.out.println("Render " + i);
            renderSky(i, canvas, stars, specialStars, random);

            if (i >= 400 && i % 10 == 0) {
                Star star = stars.get(random.nextInt(stars.size()));
                star.deltaX = random.nextDouble() * 1.0;
                star.deltaY = random.nextDouble() * 1.0;
                star.factorRadius = random.nextDouble() * 0.10 + 0.90;
            }
            if (i >= 450 && i % 50 == 0) {
                Star star = stars.get(random.nextInt(stars.size()));
                star.deltaX = 0;
                star.deltaY = 0;
                star.radius = 4;
                star.factorRadius = 0.95;
                star.color = Color.WHITE;
            }
            if (i == startCollisionFrame) {
                //double centerX = width / 2;
                //double centerY = height / 2;
                //collidingStar.deltaX = (centerX - collidingStar.x) / (nFrames - startCollisionFrame);
                //collidingStar.deltaY = (centerY - collidingStar.y) / (nFrames - startCollisionFrame);
                collidingStar.factorRadius = Math.pow(width * 2, 1.0 / (nFrames - startCollisionFrame));
            }
        }
    }

    private Star createStar(Random random, Consumer<Star> locationFunc) {
        Star star = new Star();
        locationFunc.accept(star);

        double brightness;
        if (random.nextDouble() < 0.8) {
            brightness = random.nextDouble() * 0.5 + 0.1;
        } else {
            brightness = random.nextGaussian() * 0.3 + 0.4;
        }
        brightness = clamp(brightness, 0.0, 1.0);
        star.radius = brightness * 1.1;
        star.color = Color.hsb(
                random.nextDouble() * 360,
                random.nextDouble() * 0.2,
                brightness);
        return star;
    }

    private void renderSky(int imageIndex, Canvas canvas, List<Star> stars, List<Star> moreStars, Random random) {
        int width = (int) canvas.getWidth();
        int height = (int) canvas.getHeight();

        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.setFill(Color.BLACK);
        gc.fillRect(0, 0, width, height);

        renderStars(gc, stars, random);
        renderStars(gc, moreStars, random);

        WritableImage image = new WritableImage(width, height);
        canvas.snapshot(null, image);

        RenderedImage renderedImage = SwingFXUtils.fromFXImage(image, null);
        try {
            String filename = String.format("sky/image%06d.png", imageIndex);
            ImageIO.write(renderedImage, "png", new File(filename));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void renderStars(GraphicsContext gc, List<Star> stars, Random random) {
        for (Star star : stars) {
            double randomX = random.nextGaussian() * 0.1;
            double randomY = random.nextGaussian() * 0.1;
            double randomRadius = random.nextGaussian() * 0.1;
            renderStar(gc, star, randomX, randomY, randomRadius);
        }
    }

    private void renderStar(GraphicsContext gc, Star star, double offsetX, double offsetY, double offsetRadius) {
        gc.setFill(star.color);
        double radius = star.radius + offsetRadius;
        gc.fillOval(star.x + offsetX - radius, star.y + offsetY - radius, radius * 2, radius * 2);

        star.step();
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static class Star {
        public double x;
        public double y;
        public double radius;
        public Color color;

        public double deltaX;
        public double deltaY;
        public double factorRadius = 1.0;

        public void step() {
            x += deltaX;
            y += deltaY;
            radius *= factorRadius;
        }
    }
}