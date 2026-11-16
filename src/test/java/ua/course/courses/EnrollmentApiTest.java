package ua.course.courses;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import org.junit.jupiter.api.Tag;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@Tag("api")
@SpringBootTest(classes = CoursesApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class EnrollmentApiTest {

    @LocalServerPort
    int port;

    @BeforeEach
    void setUp() {
        RestAssured.baseURI = "http://127.0.0.1";
        RestAssured.port = port;
    }

    // Helpers 

    private static String uid() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private long createStudent(int age, int score, boolean regular) {
        return given().contentType(ContentType.JSON)
                .body(Map.of("code", "S-" + uid(), "age", age, "entryScore", score, "regular", regular))
                .post("/students")
                .then().statusCode(201)
                .extract().jsonPath().getLong("id");
    }

    private long createCourse(long price, int capacity, int level) {
        return given().contentType(ContentType.JSON)
                .body(Map.of("code", "C-" + uid(), "price", price, "capacity", capacity, "level", level))
                .post("/courses")
                .then().statusCode(201)
                .extract().jsonPath().getLong("id");
    }

    private Response enroll(long studentId, long courseId, String reference) {
        return given().contentType(ContentType.JSON)
                .body(Map.of("reference", reference, "studentId", studentId,
                        "courseId", courseId, "couponValid", false))
                .post("/enrollments");
    }

    // Створює слухача (бал 80) + курс + заявку і повертає id заявки
    private long createEnrollment() {
        long s = createStudent(25, 80, false);
        long c = createCourse(10000, 5, 1);
        return enroll(s, c, "R-" + uid()).then().statusCode(201)
                .extract().jsonPath().getLong("id");
    }

    // 1. POST 

    @Test
    @DisplayName("POST /enrollments -> 201, є id і коректні поля")
    void createEnrollment_returns201() {
        long s = createStudent(25, 80, false);
        long c = createCourse(10000, 5, 1);
        String ref = "R-" + uid();

        enroll(s, c, ref).then()
                .statusCode(201)
                .header("Location", matchesPattern("/enrollments/\\d+"))
                .body("id", greaterThan(0))
                .body("reference", equalTo(ref))
                .body("studentId", equalTo((int) s))
                .body("courseId", equalTo((int) c))
                .body("total", equalTo(10000))
                .body("prepaid", equalTo(10000))
                .body("refund", equalTo(0))
                .body("status", equalTo("Enrolled"));
    }

    // 2. GET за id 

    @Test
    @DisplayName("GET /enrollments/{id} -> 200, дані збігаються зі створеними")
    void getEnrollment_returnsCreatedResource() {
        long s = createStudent(30, 90, true);
        long c = createCourse(25000, 3, 2);
        String ref = "R-" + uid();
        long id = enroll(s, c, ref).then().statusCode(201).extract().jsonPath().getLong("id");

        given().get("/enrollments/{id}", id).then()
                .statusCode(200)
                .body("id", equalTo((int) id))
                .body("reference", equalTo(ref))
                .body("studentId", equalTo((int) s))
                .body("courseId", equalTo((int) c))
                .body("total", equalTo(25000))
                .body("status", equalTo("Enrolled"));
    }

    // 3. 404 

    @Test
    @DisplayName("GET неіснуючої заявки -> 404, code і message")
    void getMissingEnrollment_returns404() {
        given().get("/enrollments/{id}", 999_999_999L).then()
                .statusCode(404)
                .body("code", equalTo("NOT_FOUND"))
                .body("message", equalTo("Заявку не знайдено"));
    }

    //  4. Action endpoint 

    @Test
    @DisplayName("POST /{id}/cancel -> 200, статус Cancelled, refund = prepaid")
    void cancelEnrollment_returns200AndNewState() {
        long id = createEnrollment();

        given().contentType(ContentType.JSON).body(Map.of("daysBeforeStart", 10))
                .post("/enrollments/{id}/cancel", id).then()
                .statusCode(200)
                .body("id", equalTo((int) id))
                .body("status", equalTo("Cancelled"))
                .body("refund", equalTo(10000))
                .body("total", equalTo(10000))
                .body("prepaid", equalTo(10000));
    }

    @Test
    @DisplayName("POST /{id}/complete -> 200, статус Completed")
    void completeEnrollment_returns200() {
        long id = createEnrollment();

        given().post("/enrollments/{id}/complete", id).then()
                .statusCode(200)
                .body("status", equalTo("Completed"))
                .body("refund", equalTo(0));
    }

    // 5.  Повторна / заборонена дія -> 409 

    @Test
    @DisplayName("Повторне скасування -> 409 ENROLLMENT_CONFLICT")
    void cancelTwice_returns409() {
        long id = createEnrollment();
        given().contentType(ContentType.JSON).body(Map.of("daysBeforeStart", 5))
                .post("/enrollments/{id}/cancel", id).then().statusCode(200);

        given().contentType(ContentType.JSON).body(Map.of("daysBeforeStart", 5))
                .post("/enrollments/{id}/cancel", id).then()
                .statusCode(409)
                .body("code", equalTo("ENROLLMENT_CONFLICT"))
                .body("message", equalTo("Зміна статусу заборонена"));
    }

    @Test
    @DisplayName("Повторне зарахування того самого слухача на курс -> 409")
    void duplicateEnrollment_returns409() {
        long s = createStudent(25, 80, false);
        long c = createCourse(10000, 5, 1);
        enroll(s, c, "R-" + uid()).then().statusCode(201);

        enroll(s, c, "R-" + uid()).then()
                .statusCode(409)
                .body("code", equalTo("ENROLLMENT_CONFLICT"))
                .body("message", not(emptyOrNullString()));
    }

    // 6. Незмінність після відхилення 

    @Test
    @DisplayName("Після 409 (cancel завершеної заявки) ресурс не змінився")
    void resourceUnchangedAfterRejectedAction() {
        long id = createEnrollment();
        given().post("/enrollments/{id}/complete", id).then().statusCode(200);

        Response before = given().get("/enrollments/{id}", id);
        before.then().statusCode(200).body("status", equalTo("Completed"));

        given().contentType(ContentType.JSON).body(Map.of("daysBeforeStart", 1))
                .post("/enrollments/{id}/cancel", id).then()
                .statusCode(409)
                .body("code", equalTo("ENROLLMENT_CONFLICT"));

        given().get("/enrollments/{id}", id).then()
                .statusCode(200)
                .body("status", equalTo("Completed"))
                .body("refund", equalTo(0))
                .body("total", equalTo(before.jsonPath().getInt("total")))
                .body("prepaid", equalTo(before.jsonPath().getInt("prepaid")));
    }

    // 7. Персональне правило (параметризація) 

    @ParameterizedTest(name = "бал={0}, regular={1} -> HTTP {2}")
    @CsvSource({
            "70, false, 201",
            "69, false, 409",
            "50, true,  201",
            "49, true,  409",
            "100, false, 201"
    })
    @DisplayName("Персональне правило допуску за балом і статусом")
    void admissionRule(int score, boolean regular, int expectedStatus) {
        long s = createStudent(25, score, regular);
        long c = createCourse(10000, 5, 1);

        Response r = enroll(s, c, "R-" + uid());
        r.then().statusCode(expectedStatus);

        if (expectedStatus == 201) {
            r.then().body("status", equalTo("Enrolled"));
        } else {
            r.then().body("code", equalTo("ENROLLMENT_CONFLICT"))
                    .body("message", equalTo("Персональні умови допуску не виконані"));
        }
    }

    // 8. Workflow 

    @Test
    @DisplayName("Workflow: POST -> GET -> cancel -> GET (id з response)")
    void workflow_create_get_cancel_get() {
        long s = createStudent(22, 75, false);
        long c = createCourse(50000, 2, 3);

        long id = enroll(s, c, "R-" + uid()).then()
                .statusCode(201).body("status", equalTo("Enrolled"))
                .extract().jsonPath().getLong("id");

        given().get("/enrollments/{id}", id).then()
                .statusCode(200).body("status", equalTo("Enrolled"));

        given().contentType(ContentType.JSON).body(Map.of("daysBeforeStart", 7))
                .post("/enrollments/{id}/cancel", id).then()
                .statusCode(200).body("status", equalTo("Cancelled"));

        given().get("/enrollments/{id}", id).then()
                .statusCode(200)
                .body("status", equalTo("Cancelled"))
                .body("refund", equalTo(50000));
    }

    // Додатково: 400

    @Test
    @DisplayName("Заявка з порожнім reference -> 400 INVALID_REQUEST")
    void blankReference_returns400() {
        long s = createStudent(25, 80, false);
        long c = createCourse(10000, 5, 1);

        enroll(s, c, "  ").then()
                .statusCode(400)
                .body("code", equalTo("INVALID_REQUEST"))
                .body("message", equalTo("Некоректний запит"));
    }
}