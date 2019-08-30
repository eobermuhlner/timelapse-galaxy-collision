package ch.obermuhlner.timelapse;

import javafx.application.Application;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
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

    private static final Color HORIZON_START_COLOR = Color.TURQUOISE;
    private static final Color HORIZON_MID_COLOR = new Color(0.0, 0.0, 0.15, 1.0);
    private static final Color HORIZON_END_COLOR = new Color(0.0, 0.0, 0.1, 1.0);

    private static final Color SKY_START_COLOR = new Color(0.5294118f, 0.80784315f, 0.98039216f, 0.0);
    private static final Color SKY_MID_COLOR = new Color(0.5294118f, 0.80784315f, 0.98039216f, 0.9);
    private static final Color SKY_END_COLOR = Color.ORANGERED;

    public static void main(String[] args) {
        launch(args);
    }

    private Random random = new Random(1);

    @Override
    public void start(Stage primaryStage) throws Exception {
        Group root = new Group();
        Scene scene = new Scene(root);

        double width = 1280;
        double height = 720;

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
        int nFrames = 2100;
        int startSwingByFrame = 600;
        int startExplosionsFrame = 700;
        int startCollisionFrame = 1500;
        int startErosionFrame = 1860;

        Polygon mountain = createMountain(width, height, height / 40, height / 5);

        List<Star> stars = new ArrayList<>();
        for (int i = 0; i < nStars1; i++) {
            stars.add(createStar(star -> {
                star.x = random.nextDouble() * width;
                star.y = random.nextDouble() * height;
            }));
        }

        double galaxyAngle = -0.758;
        double sinGalaxyAngle = Math.sin(galaxyAngle);
        double cosGalaxyAngle = Math.cos(galaxyAngle);

        double galaxyRadius = Math.min(width, height);
        for (int i = 0; i < nStars2; i++) {
            stars.add(createStar(star -> {
                do {
                    star.x = random.nextGaussian() * galaxyRadius;
                } while(star.x < -width);
                do {
                    star.y = random.nextGaussian() * galaxyRadius;
                } while(star.y < -height);
                star.x += 2.5 * width;
                star.y += -1.5 * height;
                double speed = randomDouble(1.2, 2.5);
                star.deltaX = sinGalaxyAngle * speed;
                star.deltaY = cosGalaxyAngle * speed;
                star.distance = 1.5 / speed;
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
            renderSky(i, canvas, stars, specialStars, mountain);

            if (i >= startSwingByFrame && i % 20 == 0) {
                Star star = stars.get(random.nextInt(stars.size()));
                star.deltaX = randomDouble(-1.5, 1.5);
                star.deltaY = randomDouble(-1.5, 1.5);
            }
            if (i >= startExplosionsFrame && i % 50 == 0) {
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
            if (i > startErosionFrame) {
                erodeMountain(mountain, height);
            }
        }
    }

    private Star createStar(Consumer<Star> locationFunc) {
        Star star = new Star();
        locationFunc.accept(star);

        double brightness;
        if (random.nextDouble() < 0.8) {
            brightness = random.nextDouble() * 0.5 + 0.1;
        } else {
            brightness = random.nextGaussian() * 0.3 + 0.4;
        }
        brightness /= star.distance;
        star.radius = Math.max(0.1, brightness * 1.1);
        star.color = Color.hsb(
                random.nextDouble() * 360,
                random.nextDouble() * 0.2,
                clamp(brightness, 0.0, 1.0));
        return star;
    }

    private void renderSky(int imageIndex, Canvas canvas, List<Star> stars, List<Star> specialStars, Polygon mountain) {
        int width = (int) canvas.getWidth();
        int height = (int) canvas.getHeight();

        // render background
        GraphicsContext gc = canvas.getGraphicsContext2D();
        RadialGradient skyGradient = new RadialGradient(
                0,
                0,
                width / 2,
                height * 2,
                height * 2,
                false,
                CycleMethod.NO_CYCLE,
                new Stop(0, HORIZON_START_COLOR),
                new Stop(0.8, HORIZON_MID_COLOR),
                new Stop(1.0, HORIZON_END_COLOR));
        gc.setFill(skyGradient);
        gc.fillRect(0, 0, width, height);

        // render stars
        renderStars(gc, stars);

        // render blue sky (depends on special stars)
        if (!specialStars.isEmpty()) {
            double radius = specialStars.stream().mapToDouble(star -> star.radius).max().getAsDouble();
            final double startRadius = 6;
            final double midRadius = 40;
            final double endRadius = 200;
            if (radius > startRadius && radius <= midRadius) {
                double gradient = smoothstep((radius - startRadius) / (midRadius - startRadius), 0.0, 1.0);
                gc.setFill(SKY_START_COLOR.interpolate(SKY_MID_COLOR, gradient));
                gc.fillRect(0, 0, width, height);
            } else if (radius > midRadius) {
                double gradient = smoothstep((radius - midRadius) / (endRadius - midRadius), 0.0, 1.0);
                gc.setFill(SKY_MID_COLOR.interpolate(SKY_END_COLOR, gradient));
                gc.fillRect(0, 0, width, height);
            }
        }

        // render special stars
        renderStars(gc, specialStars);

        // render horizon
        gc.setFill(Color.BLACK);
        gc.fillPolygon(mountain.xPoints, mountain.yPoints, mountain.xPoints.length);

        // snapshot
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

    private void renderStars(GraphicsContext gc, List<Star> stars) {
        for (Star star : stars) {
            double randomX = random.nextGaussian() * 0.1;
            double randomY = random.nextGaussian() * 0.1;
            double randomRadius = random.nextGaussian() * 0.1;
            renderStar(gc, star, randomX, randomY, randomRadius);
        }
    }

    private void renderStar(GraphicsContext gc, Star star, double offsetX, double offsetY, double offsetRadius) {
        double centerX = star.x + offsetX;
        double centerY = star.y + offsetY;
        double radius = star.radius + offsetRadius;

        if (radius > 6) {
            radius = 0.01 * radius * radius + radius * 2;
            RadialGradient starGradient = new RadialGradient(
                    0,
                    0,
                    centerX,
                    centerY,
                    radius,
                    false,
                    CycleMethod.NO_CYCLE,
                    new Stop(0.00, star.color),
                    new Stop(0.50, star.color),
                    new Stop(0.55, new Color(star.color.getRed(), star.color.getGreen(), star.color.getBlue(), 0.2)),
                    new Stop(1.00, new Color(star.color.getRed(), star.color.getGreen(), star.color.getBlue(), 0.0)));
            gc.setFill(starGradient);
        } else {
            gc.setFill(star.color);
        }

        gc.fillOval(centerX - radius, centerY - radius, radius * 2, radius * 2);

        star.step();
    }

    private Polygon createMountain(double width, double height, double minHeight, double maxHeight) {
        List<Double> mountainPoints = new ArrayList<>();
        mountainPoints.add(minHeight);
        mountainPoints.add(maxHeight);
        mountainPoints.add(minHeight);

        double maxDisplacement = 1.0;
        for (int i = 0; i < 7; i++) {
            for (int j = mountainPoints.size()-1; j > 0 ; j--) {
                double height1 = mountainPoints.get(j);
                double height2 = mountainPoints.get(j-1);
                double mid = (height1 + height2) / 2;
                double displacement = randomDouble(-maxDisplacement, maxDisplacement);
                double midHeight = mid + Math.max(minHeight, mid - minHeight) * displacement - 0.5;
                mountainPoints.add(j, midHeight);
            }
            maxDisplacement = maxDisplacement / 2;
        }

        Polygon polygon = new Polygon();
        polygon.xPoints = new double[mountainPoints.size() + 2];
        polygon.yPoints = new double[mountainPoints.size() + 2];
        for (int i = 0; i < mountainPoints.size(); i++) {
            polygon.xPoints[i] = width / (mountainPoints.size()-1) * i;
            polygon.yPoints[i] = height - mountainPoints.get(i);
        }
        polygon.xPoints[mountainPoints.size()] = width;
        polygon.yPoints[mountainPoints.size()] = height;
        polygon.xPoints[mountainPoints.size()+1] = 0;
        polygon.yPoints[mountainPoints.size()+1] = height;

        return polygon;
    }

    private void erodeMountain(Polygon mountain, double height) {
        for (int i = 0; i < mountain.yPoints.length - 2; i++) {
            double erosion = randomDouble(0.985, 0.990);
            double pointHeight = height - mountain.yPoints[i];
            pointHeight = pointHeight * erosion;
            mountain.yPoints[i] = height - pointHeight;
        }
    }

    private double randomDouble(double min, double max) {
        if (min == max) {
            return min;
        }

        return random.nextDouble() * (max - min) + min;
    }

    private static double smoothstep(double value, double edge0, double edge1) {
        value = clamp((value - edge0) / (edge1 - edge0), 0.0, 1.0);
        return value * value * (3 - 2 * value);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static class Star {
        public double x;
        public double y;
        public double distance = 1.0;
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

    private static class Polygon {
        public double[] xPoints;
        public double[] yPoints;
    }
}
