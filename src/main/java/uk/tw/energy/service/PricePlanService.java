package uk.tw.energy.service;

import org.springframework.stereotype.Service;
import uk.tw.energy.domain.ElectricityReading;
import uk.tw.energy.domain.PricePlan;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class PricePlanService {

    private final List<PricePlan> pricePlans;
    private final MeterReadingService meterReadingService;

    public PricePlanService(List<PricePlan> pricePlans, MeterReadingService meterReadingService) {
        this.pricePlans = pricePlans;
        this.meterReadingService = meterReadingService;
    }

    public Optional<Map<String, BigDecimal>> getConsumptionCostOfElectricityReadingsForEachPricePlan(String smartMeterId) {
        Optional<List<ElectricityReading>> electricityReadings = meterReadingService.getReadings(smartMeterId);

        return electricityReadings.map(readings -> pricePlans.stream().collect(
                Collectors.toMap(PricePlan::getPlanName, t -> calculateCost(readings, t))));
    }

    BigDecimal calculateCost(List<ElectricityReading> electricityReadings, PricePlan pricePlan) {
        BigDecimal average = calculateAverageReading(electricityReadings);
        BigDecimal timeElapsed = calculateTimeElapsed(electricityReadings);

        BigDecimal averagedCost = average.divide(timeElapsed, RoundingMode.HALF_UP);
        return averagedCost.multiply(pricePlan.getUnitRate());
    }

    BigDecimal calculateAverageReading(List<ElectricityReading> electricityReadings) {
        BigDecimal summedReadings = electricityReadings.stream()
                .map(ElectricityReading::getReading)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return summedReadings.divide(BigDecimal.valueOf(electricityReadings.size()), RoundingMode.HALF_UP);
    }

    BigDecimal calculateTimeElapsed(List<ElectricityReading> electricityReadings) {
        Optional<ElectricityReading> first = electricityReadings.stream()
                .min(Comparator.comparing(ElectricityReading::getTime));

        Optional<ElectricityReading> last = electricityReadings.stream()
                .max(Comparator.comparing(ElectricityReading::getTime));

        if (!shouldBothPresent(first, last)) {
            return BigDecimal.ZERO;
        }

        return BigDecimal.valueOf(Duration.between(first.get().getTime(), last.get().getTime()).getSeconds() / 3600.0);
    }

    private boolean shouldBothPresent(Optional<ElectricityReading> first, Optional<ElectricityReading> last) {
        return first.isPresent() && last.isPresent();
    }
}
