package de.flubba;

public enum DiffusionMatrix {
    FLOYD_STEINBERG(new double[][]{
            {0, 0, 7},
            {3, 5, 1}
    }),
    JARVIS_JUDICE_NINKE(new double[][]{
            {0, 0, 0, 7, 5},
            {3, 5, 7, 5, 3},
            {1, 3, 5, 3, 1}
    }),
    SIERRA_LITE(new double[][]{
            {0, 0, 2},
            {1, 1, 0}
    }),
    FLUBBA(new double[][]{
            {0, 0, 0, 10},
            {1, 4, 7, 2},
            {1, 3, 1, 0}
    });

    public final double[][] matrix;

    DiffusionMatrix(double[][] matrix) {
        this.matrix = matrix;
        var divisor = calculateDivisor(matrix);
        for (int i = 0; i < matrix.length; i++) {
            for (int j = 0; j < matrix[i].length; j++) {
                matrix[i][j] /= divisor;
            }
        }
    }

    private static double calculateDivisor(double[][] matrix) {
        double sum = 0;
        for (double[] row : matrix) {
            for (double value : row) {
                sum += value;
            }
        }
        return (sum == 0) ? 1 : sum;
    }

}
