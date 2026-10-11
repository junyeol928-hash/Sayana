public class Shape {
    private final String name;

    Shape(String name) {
        this.name = name;
    }

    String getName() {
        return name;
    }

    double area() {
        return 0.0;
    }

    String describe() {
        return name + "の面積: " + area();
    }

    public static void main(String[] args) {
        System.out.println(new Rectangle(3, 4).describe()); // 長方形の面積: 12.0
        System.out.println(new Triangle(3, 5).describe());  // 三角形の面積: 7.5
    }
}

class Rectangle extends Shape {
    private final int width;
    private final int height;

    Rectangle(int width, int height) {
        super("長方形");
        this.width = width;
        this.height = height;
    }

    @Override
    double area() {
        return (double) width * height;
    }
}

class Triangle extends Shape {
    private final int base;
    private final int height;

    Triangle(int base, int height) {
        super("三角形");
        this.base = base;
        this.height = height;
    }

    @Override
    double area() {
        return base * height / 2.0;
    }
}
