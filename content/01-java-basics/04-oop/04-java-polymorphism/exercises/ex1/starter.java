public class AreaCalculator {

    static double totalArea(Shape[] shapes) {
        // TODO: すべての図形の area() を合計して返す
        return 0.0;
    }

    static int countLargerThan(Shape[] shapes, double limit) {
        // TODO: area() が limit より大きい図形の個数を返す
        return 0;
    }

    public static void main(String[] args) {
        Shape[] shapes = { new Rectangle(3, 4), new Triangle(4, 5), new Circle(1) };
        System.out.println(totalArea(shapes));             // 25.14159...
        System.out.println(countLargerThan(shapes, 10.0)); // 1
    }
}

class Shape {
    double area() {
        return 0.0;
    }
}

class Circle extends Shape {
    private final double radius;

    Circle(double radius) {
        this.radius = radius;
    }

    @Override
    double area() {
        return Math.PI * radius * radius;
    }
}

class Rectangle extends Shape {
    private final double width;
    private final double height;

    Rectangle(double width, double height) {
        this.width = width;
        this.height = height;
    }

    @Override
    double area() {
        return width * height;
    }
}

class Triangle extends Shape {
    private final double base;
    private final double height;

    Triangle(double base, double height) {
        this.base = base;
        this.height = height;
    }

    @Override
    double area() {
        return base * height / 2;
    }
}
