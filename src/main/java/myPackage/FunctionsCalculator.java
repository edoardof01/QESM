//FunctionsCalculator.java
package myPackage;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class FunctionsCalculator {

    //CDF normalizzata
    public static List<BigDecimal> calculateCDF(List<BigDecimal> interArrivalTimes) {
        // Ordina i tempi in ordine crescente
        Collections.sort(interArrivalTimes);

        List<BigDecimal> cdf = new ArrayList<>();
        int totalElements = interArrivalTimes.size();

        for (int i = 0; i < totalElements; i++) {
            // La probabilità cumulativa è calcolata usando RoundingMode.HALF_UP
            BigDecimal cumulativeProbability = new BigDecimal(i + 1)
                    .divide(new BigDecimal(totalElements), 10, RoundingMode.HALF_UP);
            cdf.add(cumulativeProbability);
        }
        return cdf;
    }

    public static List<BigDecimal> calculatePDF(List<BigDecimal> cdf) {
        List<BigDecimal> pdf = new ArrayList<>();

        for (int i = 0; i < cdf.size(); i++) {
            if (i == 0) {
                pdf.add(cdf.get(i)); // La prima PDF è uguale al primo valore della CDF
            } else {
                BigDecimal difference = cdf.get(i).subtract(cdf.get(i - 1));
                pdf.add(difference);
            }
        }

        return pdf;
    }
}
