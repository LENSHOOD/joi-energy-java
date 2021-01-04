package uk.tw.energy.service;

import static org.assertj.core.api.AssertionsForClassTypes.*;
import static org.junit.jupiter.api.Assertions.*;

import org.assertj.core.api.AssertionsForClassTypes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import uk.tw.energy.domain.ElectricityReading;
import uk.tw.energy.domain.PricePlan;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

class PricePlanServiceTest {
    private final MeterReadingService meterReadingService = Mockito.mock(MeterReadingService.class);
    private PricePlanService pricePlanService;

    @BeforeEach
    void setUp() {
        pricePlanService = new PricePlanService(new ArrayList<>(), meterReadingService);
    }

    @Test
    void givenThreeElectricityReadingsShouldReturnTheElapseHoursBetweenLatestAndFarthest() {
        // given
        ElectricityReading latest = new ElectricityReading(
                LocalDateTime.of(2021, 1, 4, 23, 25, 35).toInstant(ZoneOffset.UTC),
                BigDecimal.valueOf(1));
        ElectricityReading middle = new ElectricityReading(
                LocalDateTime.of(2020, 12, 12, 12, 12, 21).toInstant(ZoneOffset.UTC),
                BigDecimal.valueOf(2));
        ElectricityReading farthest = new ElectricityReading(
                LocalDateTime.of(2019, 8, 8, 8, 8, 8).toInstant(ZoneOffset.UTC),
                BigDecimal.valueOf(3));

        // when
        BigDecimal elapsedHours = pricePlanService.calculateTimeElapsed(Arrays.asList(latest, middle, farthest));

        // then
        assertThat(elapsedHours).isEqualTo(new BigDecimal("12375.290833333333"));
    }

    @Test
    void givenOneElectricityReadingsShouldReturnZeroElapseHours() {
        // given
        ElectricityReading onlyOneReading = new ElectricityReading(
                LocalDateTime.of(2021, 1, 4, 23, 25, 35).toInstant(ZoneOffset.UTC),
                BigDecimal.valueOf(1));

        // when
        BigDecimal elapsedHours = pricePlanService.calculateTimeElapsed(Collections.singletonList(onlyOneReading));

        // then
        assertThat(elapsedHours).isEqualTo(BigDecimal.valueOf(0.0));
    }

    @Test
    void givenThreeElectricityReadingsShouldReturnAverageReadingWithHalfUp() {
        // given
        ElectricityReading latest = new ElectricityReading(
                LocalDateTime.of(2021, 1, 4, 23, 25, 35).toInstant(ZoneOffset.UTC),
                BigDecimal.valueOf(1111.11));
        ElectricityReading middle = new ElectricityReading(
                LocalDateTime.of(2020, 12, 12, 12, 12, 21).toInstant(ZoneOffset.UTC),
                BigDecimal.valueOf(200));
        ElectricityReading farthest = new ElectricityReading(
                LocalDateTime.of(2019, 8, 8, 8, 8, 8).toInstant(ZoneOffset.UTC),
                BigDecimal.valueOf(3333.44));

        // when
        BigDecimal averageReading = pricePlanService.calculateAverageReading(Arrays.asList(latest, middle, farthest));

        // then
        assertThat(averageReading).isEqualTo(new BigDecimal("1548.18"));
    }

    @Test
    void givenThreeElectricityReadingsShouldReturnUnitRateByReadingPerHour() {
        // given
        ElectricityReading latest = new ElectricityReading(
                LocalDateTime.of(2021, 1, 4, 23, 25, 35).toInstant(ZoneOffset.UTC),
                BigDecimal.valueOf(1111.11));
        ElectricityReading middle = new ElectricityReading(
                LocalDateTime.of(2020, 12, 12, 12, 12, 21).toInstant(ZoneOffset.UTC),
                BigDecimal.valueOf(200));
        ElectricityReading farthest = new ElectricityReading(
                LocalDateTime.of(2019, 8, 8, 8, 8, 8).toInstant(ZoneOffset.UTC),
                BigDecimal.valueOf(3333.44));
        PricePlan fakePlan =
                new PricePlan("fake-plan", "fake-supplier", BigDecimal.valueOf(0.5), Collections.emptyList());

        // when
        BigDecimal cost = pricePlanService.calculateCost(Arrays.asList(latest, middle, farthest), fakePlan);

        // then
        assertThat(cost).isEqualTo(new BigDecimal("0.065"));
    }

    @Test
    void givenThreeElectricityReadingsAndTwoPricePlansShouldReturnEachPlanAveragePrice() {
        // given
        ElectricityReading latest = new ElectricityReading(
                LocalDateTime.of(2021, 1, 4, 23, 25, 35).toInstant(ZoneOffset.UTC),
                BigDecimal.valueOf(1111.11));
        ElectricityReading middle = new ElectricityReading(
                LocalDateTime.of(2020, 12, 12, 12, 12, 21).toInstant(ZoneOffset.UTC),
                BigDecimal.valueOf(200));
        ElectricityReading farthest = new ElectricityReading(
                LocalDateTime.of(2019, 8, 8, 8, 8, 8).toInstant(ZoneOffset.UTC),
                BigDecimal.valueOf(3333.44));

        PricePlan fakePlan1 =
                new PricePlan("fake-plan-1.1", "fake-supplier-1", BigDecimal.valueOf(1.1), Collections.emptyList());
        PricePlan fakePlan2 =
                new PricePlan("fake-plan-tuesday-half-price", "fake-supplier-2", BigDecimal.valueOf(1),
                        Collections.singletonList(new PricePlan.PeakTimeMultiplier(DayOfWeek.TUESDAY, BigDecimal.valueOf(0.5))));

        pricePlanService = new PricePlanService(Arrays.asList(fakePlan1, fakePlan2), meterReadingService);

        // when
        String meterId = "fakeId";
        Mockito.when(meterReadingService.getReadings(meterId)).thenReturn(Optional.of(Arrays.asList(latest, middle, farthest)));
        Optional<Map<String, BigDecimal>> planWithCost =
                pricePlanService.getConsumptionCostOfElectricityReadingsForEachPricePlan(meterId);

        // then
        assertTrue(planWithCost.isPresent());
        assertThat(planWithCost.get().get("fake-plan-1.1")).isEqualTo(new BigDecimal("0.143"));
        assertThat(planWithCost.get().get("fake-plan-tuesday-half-price")).isEqualTo(new BigDecimal("0.13"));
    }

    @Test
    void givenNoElectricityReadingsShouldReturnEmpty() {
        // given
        String meterId = "fakeId";
        Mockito.when(meterReadingService.getReadings(meterId)).thenReturn(Optional.empty());

        // when
        Optional<Map<String, BigDecimal>> planWithCost =
                pricePlanService.getConsumptionCostOfElectricityReadingsForEachPricePlan(meterId);

        // then
        assertFalse(planWithCost.isPresent());
    }
}