package ua.course.courses;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class EnrollmentServiceUnitTest {

    private EnrollmentService service;

    @BeforeEach
    void setUp() {
        service = new EnrollmentService(
                mock(StudentRepository.class),
                mock(CourseRepository.class),
                mock(EnrollmentRepository.class),
                (student, course) -> true);
    }

    @Test
    void score70IsEligibleForEveryone() {
        assertTrue(service.isEligible(25, 70, 1, false));
    }

    @Test
    void score69IsNotEligibleForNonRegular() {
        assertFalse(service.isEligible(25, 69, 1, false));
    }

    @Test
    void score50IsEligibleForRegular() {
        assertTrue(service.isEligible(25, 50, 1, true));
    }

    @Test
    void score49IsNotEligibleEvenForRegular() {
        assertFalse(service.isEligible(25, 49, 1, true));
    }

    @Test
    void totalEqualsPriceWithoutDiscount() {
        assertEquals(10000, service.calculateTotal(10000, false, false));
    }

    @Test
    void refundIsFullPrepaid() {
        assertEquals(10000, service.calculateRefund(10000, 5));
    }

    @Test
    void invalidAgeThrows() {
        assertThrows(IllegalArgumentException.class, () -> service.isEligible(13, 80, 1, false));
    }

    @Test
    void invalidPriceThrows() {
        assertThrows(IllegalArgumentException.class, () -> service.calculateTotal(50, false, false));
    }
}