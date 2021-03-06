package pro.taskana.classification.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import pro.taskana.classification.api.ClassificationService;
import pro.taskana.classification.api.exceptions.ClassificationNotFoundException;
import pro.taskana.classification.rest.assembler.ClassificationRepresentationModelAssembler;
import pro.taskana.classification.rest.models.ClassificationRepresentationModel;
import pro.taskana.classification.rest.models.ClassificationSummaryRepresentationModel;
import pro.taskana.common.rest.Mapping;
import pro.taskana.common.rest.RestHelper;
import pro.taskana.common.rest.TaskanaSpringBootTest;
import pro.taskana.common.rest.models.TaskanaPagedModel;
import pro.taskana.common.rest.models.TaskanaPagedModelKeys;

/** Test classification definitions. */
@TaskanaSpringBootTest
class ClassificationDefinitionControllerIntTest {

  private static final ParameterizedTypeReference<
          TaskanaPagedModel<ClassificationRepresentationModel>>
      CLASSIFICATION_PAGE_MODEL_TYPE =
          new ParameterizedTypeReference<TaskanaPagedModel<ClassificationRepresentationModel>>() {};
  private static final Logger LOGGER = LoggerFactory.getLogger(ClassificationController.class);
  private static RestTemplate template;

  private final RestHelper restHelper;
  private final ObjectMapper mapper;
  private final ClassificationService classificationService;
  private final ClassificationRepresentationModelAssembler classificationAssembler;

  @Autowired
  ClassificationDefinitionControllerIntTest(
      RestHelper restHelper,
      ObjectMapper mapper,
      ClassificationService classificationService,
      ClassificationRepresentationModelAssembler classificationAssembler) {
    this.restHelper = restHelper;
    this.mapper = mapper;
    this.classificationService = classificationService;
    this.classificationAssembler = classificationAssembler;
  }

  @BeforeAll
  static void init() {
    template = RestHelper.TEMPLATE;
  }

  @Test
  void testExportClassifications() {
    ResponseEntity<TaskanaPagedModel<ClassificationRepresentationModel>> response =
        template.exchange(
            restHelper.toUrl(Mapping.URL_CLASSIFICATIONDEFINITIONS) + "?domain=DOMAIN_B",
            HttpMethod.GET,
            restHelper.defaultRequest(),
            CLASSIFICATION_PAGE_MODEL_TYPE);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getContent())
        .extracting(ClassificationRepresentationModel::getClassificationId)
        .containsOnlyOnce(
            "CLI:200000000000000000000000000000000015",
            "CLI:200000000000000000000000000000000017",
            "CLI:200000000000000000000000000000000018",
            "CLI:200000000000000000000000000000000003",
            "CLI:200000000000000000000000000000000004");
  }

  @Test
  void testExportClassificationsFromWrongDomain() {
    ResponseEntity<TaskanaPagedModel<ClassificationRepresentationModel>> response =
        template.exchange(
            restHelper.toUrl(Mapping.URL_CLASSIFICATIONDEFINITIONS) + "?domain=ADdfe",
            HttpMethod.GET,
            restHelper.defaultRequest(),
            CLASSIFICATION_PAGE_MODEL_TYPE);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getContent()).isEmpty();
  }

  @Test
  void testImportFilledClassification() throws IOException {
    ClassificationRepresentationModel classification = new ClassificationRepresentationModel();
    classification.setClassificationId("classificationId_");
    classification.setKey("key drelf");
    classification.setParentId("CLI:100000000000000000000000000000000016");
    classification.setParentKey("T2000");
    classification.setCategory("MANUAL");
    classification.setType("TASK");
    classification.setDomain("DOMAIN_A");
    classification.setIsValidInDomain(true);
    classification.setCreated(Instant.parse("2016-05-12T10:12:12.12Z"));
    classification.setModified(Instant.parse("2018-05-12T10:12:12.12Z"));
    classification.setName("name");
    classification.setDescription("description");
    classification.setPriority(4);
    classification.setServiceLevel("P2D");
    classification.setApplicationEntryPoint("entry1");
    classification.setCustom1("custom");
    classification.setCustom2("custom");
    classification.setCustom3("custom");
    classification.setCustom4("custom");
    classification.setCustom5("custom");
    classification.setCustom6("custom");
    classification.setCustom7("custom");
    classification.setCustom8("custom");

    TaskanaPagedModel<ClassificationRepresentationModel> clList =
        new TaskanaPagedModel<>(
            TaskanaPagedModelKeys.CLASSIFICATIONS, Collections.singletonList(classification));

    ResponseEntity<Void> response = importRequest(clList);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
  }

  @Test
  void testFailureWhenKeyIsMissing() throws IOException {
    ClassificationRepresentationModel classification = new ClassificationRepresentationModel();
    classification.setDomain("DOMAIN_A");
    TaskanaPagedModel<ClassificationRepresentationModel> clList =
        new TaskanaPagedModel<>(
            TaskanaPagedModelKeys.CLASSIFICATIONS, Collections.singletonList(classification));

    try {
      importRequest(clList);
    } catch (HttpClientErrorException e) {
      assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
  }

  @Test
  void testFailureWhenDomainIsMissing() throws IOException {
    ClassificationRepresentationModel classification = new ClassificationRepresentationModel();
    classification.setKey("one");
    TaskanaPagedModel<ClassificationRepresentationModel> clList =
        new TaskanaPagedModel<>(
            TaskanaPagedModelKeys.CLASSIFICATIONS, Collections.singletonList(classification));

    try {
      importRequest(clList);
    } catch (HttpClientErrorException e) {
      assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
  }

  @Test
  void testFailureWhenUpdatingTypeOfExistingClassification() throws Exception {
    ClassificationRepresentationModel classification =
        getClassificationWithKeyAndDomain("T6310", "");
    classification.setType("DOCUMENT");
    TaskanaPagedModel<ClassificationRepresentationModel> clList =
        new TaskanaPagedModel<>(
            TaskanaPagedModelKeys.CLASSIFICATIONS, Collections.singletonList(classification));

    assertThatThrownBy(() -> importRequest(clList))
        .isInstanceOf(HttpClientErrorException.class)
        .extracting(e -> (HttpClientErrorException) e)
        .extracting(HttpClientErrorException::getStatusCode)
        .isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void testImportMultipleClassifications() throws IOException {
    ClassificationRepresentationModel classification1 =
        this.createClassification("id1", "ImportKey1", "DOMAIN_A", null, null);

    ClassificationRepresentationModel classification2 =
        this.createClassification(
            "id2", "ImportKey2", "DOMAIN_A", "CLI:100000000000000000000000000000000016", "T2000");
    classification2.setCategory("MANUAL");
    classification2.setType("TASK");
    classification2.setIsValidInDomain(true);
    classification2.setCreated(Instant.parse("2016-05-12T10:12:12.12Z"));
    classification2.setModified(Instant.parse("2018-05-12T10:12:12.12Z"));
    classification2.setName("name");
    classification2.setDescription("description");
    classification2.setPriority(4);
    classification2.setServiceLevel("P2D");
    classification2.setApplicationEntryPoint("entry1");

    TaskanaPagedModel<ClassificationRepresentationModel> clList =
        new TaskanaPagedModel<>(
            TaskanaPagedModelKeys.CLASSIFICATIONS, Arrays.asList(classification1, classification2));

    ResponseEntity<Void> response = importRequest(clList);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
  }

  @Test
  void testImportDuplicateClassification() {
    ClassificationRepresentationModel classification1 = new ClassificationRepresentationModel();
    classification1.setClassificationId("id1");
    classification1.setKey("ImportKey3");
    classification1.setDomain("DOMAIN_A");

    TaskanaPagedModel<ClassificationRepresentationModel> clList =
        new TaskanaPagedModel<>(
            TaskanaPagedModelKeys.CLASSIFICATIONS, Arrays.asList(classification1, classification1));

    assertThatThrownBy(() -> importRequest(clList))
        .isInstanceOf(HttpClientErrorException.class)
        .extracting(e -> (HttpClientErrorException) e)
        .extracting(HttpStatusCodeException::getStatusCode)
        .isEqualTo(HttpStatus.CONFLICT);
  }

  @Test
  void testInsertExistingClassificationWithOlderTimestamp() throws Exception {
    ClassificationRepresentationModel existingClassification =
        getClassificationWithKeyAndDomain("L110107", "DOMAIN_A");
    existingClassification.setName("first new Name");

    TaskanaPagedModel<ClassificationRepresentationModel> clList =
        new TaskanaPagedModel<>(
            TaskanaPagedModelKeys.CLASSIFICATIONS,
            Collections.singletonList(existingClassification));

    ResponseEntity<Void> response = importRequest(clList);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

    existingClassification.setName("second new Name");
    clList =
        new TaskanaPagedModel<>(
            TaskanaPagedModelKeys.CLASSIFICATIONS,
            Collections.singletonList(existingClassification));

    response = importRequest(clList);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

    ClassificationRepresentationModel testClassification =
        this.getClassificationWithKeyAndDomain("L110107", "DOMAIN_A");
    assertThat(testClassification.getName()).isEqualTo("second new Name");
  }

  @Test
  void testHookExistingChildToNewParent() throws Exception {
    final ClassificationRepresentationModel newClassification =
        createClassification("new Classification", "newClass", "DOMAIN_A", null, "L11010");
    ClassificationRepresentationModel existingClassification =
        getClassificationWithKeyAndDomain("L110102", "DOMAIN_A");
    existingClassification.setParentId("new Classification");
    existingClassification.setParentKey("newClass");

    TaskanaPagedModel<ClassificationRepresentationModel> clList =
        new TaskanaPagedModel<>(
            TaskanaPagedModelKeys.CLASSIFICATIONS,
            Arrays.asList(existingClassification, newClassification));

    ResponseEntity<Void> response = importRequest(clList);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

    ClassificationSummaryRepresentationModel parentCl =
        getClassificationWithKeyAndDomain("L11010", "DOMAIN_A");
    ClassificationSummaryRepresentationModel testNewCl =
        getClassificationWithKeyAndDomain("newClass", "DOMAIN_A");
    ClassificationSummaryRepresentationModel testExistingCl =
        getClassificationWithKeyAndDomain("L110102", "DOMAIN_A");

    assertThat(parentCl).isNotNull();
    assertThat(testNewCl).isNotNull();
    assertThat(testExistingCl).isNotNull();
    assertThat(testExistingCl.getParentId()).isEqualTo(testNewCl.getClassificationId());
    assertThat(testNewCl.getParentId()).isEqualTo(parentCl.getClassificationId());
  }

  @Test
  void testImportParentAndChildClassification() throws Exception {
    ClassificationRepresentationModel classification1 =
        this.createClassification("parentId", "ImportKey6", "DOMAIN_A", null, null);
    ClassificationRepresentationModel classification2 =
        this.createClassification("childId1", "ImportKey7", "DOMAIN_A", null, "ImportKey6");
    ClassificationRepresentationModel classification3 =
        this.createClassification("childId2", "ImportKey8", "DOMAIN_A", "parentId", null);
    ClassificationRepresentationModel classification4 =
        this.createClassification(
            "grandchildId1", "ImportKey9", "DOMAIN_A", "childId1", "ImportKey7");
    ClassificationRepresentationModel classification5 =
        this.createClassification("grandchild2", "ImportKey10", "DOMAIN_A", null, "ImportKey7");

    TaskanaPagedModel<ClassificationRepresentationModel> clList =
        new TaskanaPagedModel<>(
            TaskanaPagedModelKeys.CLASSIFICATIONS,
            Arrays.asList(
                classification1,
                classification2,
                classification3,
                classification4,
                classification5));

    ResponseEntity<Void> response = importRequest(clList);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

    ClassificationRepresentationModel parentCl =
        getClassificationWithKeyAndDomain("ImportKey6", "DOMAIN_A");
    ClassificationRepresentationModel childCl =
        getClassificationWithKeyAndDomain("ImportKey7", "DOMAIN_A");
    ClassificationRepresentationModel grandchildCl =
        getClassificationWithKeyAndDomain("ImportKey9", "DOMAIN_A");

    assertThat(parentCl).isNotNull();
    assertThat(childCl).isNotNull();
    assertThat(grandchildCl).isNotNull();
    assertThat(grandchildCl.getParentId()).isEqualTo(childCl.getClassificationId());
    assertThat(childCl.getParentId()).isEqualTo(parentCl.getClassificationId());
  }

  @Test
  void testImportParentAndChildClassificationWithKey() throws Exception {
    ClassificationRepresentationModel parent =
        createClassification("parent", "ImportKey11", "DOMAIN_A", null, null);
    parent.setCustom1("parent is correct");
    ClassificationRepresentationModel wrongParent =
        createClassification("wrongParent", "ImportKey11", "DOMAIN_B", null, null);
    ClassificationRepresentationModel child =
        createClassification("child", "ImportKey13", "DOMAIN_A", null, "ImportKey11");

    TaskanaPagedModel<ClassificationRepresentationModel> clList =
        new TaskanaPagedModel<>(
            TaskanaPagedModelKeys.CLASSIFICATIONS, Arrays.asList(parent, wrongParent, child));

    ResponseEntity<Void> response = importRequest(clList);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

    ClassificationRepresentationModel rightParentCl =
        getClassificationWithKeyAndDomain("ImportKey11", "DOMAIN_A");
    ClassificationRepresentationModel wrongParentCl =
        getClassificationWithKeyAndDomain("ImportKey11", "DOMAIN_B");
    ClassificationRepresentationModel childCl =
        getClassificationWithKeyAndDomain("ImportKey13", "DOMAIN_A");

    assertThat(rightParentCl).isNotNull();
    assertThat(wrongParentCl).isNotNull();
    assertThat(childCl).isNotNull();
    assertThat(childCl.getParentId()).isEqualTo(rightParentCl.getClassificationId());
    assertThat(childCl.getParentId()).isNotEqualTo(wrongParentCl.getClassificationId());
  }

  @Test
  void testChangeParentByImportingExistingClassification() throws Exception {
    ClassificationRepresentationModel child1 =
        this.getClassificationWithKeyAndDomain("L110105", "DOMAIN_A");
    assertThat(child1.getParentKey()).isEqualTo("L11010");
    child1.setParentId("CLI:100000000000000000000000000000000002");
    child1.setParentKey("L10303");

    ClassificationRepresentationModel child2 =
        this.getClassificationWithKeyAndDomain("L110107", "DOMAIN_A");
    assertThat(child2.getParentKey()).isEqualTo("L11010");
    child2.setParentId("");
    child2.setParentKey("");

    TaskanaPagedModel<ClassificationRepresentationModel> clList =
        new TaskanaPagedModel<>(
            TaskanaPagedModelKeys.CLASSIFICATIONS, Arrays.asList(child1, child2));

    ResponseEntity<Void> response = importRequest(clList);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    Thread.sleep(10);
    LOGGER.debug("Wait 10 ms to give the system a chance to update");

    ClassificationRepresentationModel childWithNewParent =
        this.getClassificationWithKeyAndDomain("L110105", "DOMAIN_A");
    assertThat(childWithNewParent.getParentKey()).isEqualTo(child1.getParentKey());

    ClassificationRepresentationModel childWithoutParent =
        this.getClassificationWithKeyAndDomain("L110107", "DOMAIN_A");
    assertThat(childWithoutParent.getParentId()).isEqualTo(child2.getParentId());
    assertThat(childWithoutParent.getParentKey()).isEqualTo(child2.getParentKey());
  }

  @Test
  void testFailOnImportDuplicates() throws Exception {
    ClassificationRepresentationModel classification =
        this.getClassificationWithKeyAndDomain("L110105", "DOMAIN_A");

    TaskanaPagedModel<ClassificationRepresentationModel> clList =
        new TaskanaPagedModel<>(
            TaskanaPagedModelKeys.CLASSIFICATIONS, Arrays.asList(classification, classification));

    assertThatThrownBy(() -> importRequest(clList))
        .isInstanceOf(HttpClientErrorException.class)
        .extracting(ex -> ((HttpClientErrorException) ex).getStatusCode())
        .isEqualTo(HttpStatus.CONFLICT);
  }

  private ClassificationRepresentationModel createClassification(
      String id, String key, String domain, String parentId, String parentKey) {
    ClassificationRepresentationModel classificationRepresentationModel =
        new ClassificationRepresentationModel();
    classificationRepresentationModel.setClassificationId(id);
    classificationRepresentationModel.setKey(key);
    classificationRepresentationModel.setDomain(domain);
    classificationRepresentationModel.setParentId(parentId);
    classificationRepresentationModel.setParentKey(parentKey);
    return classificationRepresentationModel;
  }

  private ClassificationRepresentationModel getClassificationWithKeyAndDomain(
      String key, String domain) throws ClassificationNotFoundException {
    return classificationAssembler.toModel(classificationService.getClassification(key, domain));
  }

  private ResponseEntity<Void> importRequest(
      TaskanaPagedModel<ClassificationRepresentationModel> clList) throws IOException {
    LOGGER.debug("Start Import");
    File tmpFile = File.createTempFile("test", ".tmp");
    OutputStreamWriter writer =
        new OutputStreamWriter(new FileOutputStream(tmpFile), StandardCharsets.UTF_8);
    mapper.writeValue(writer, clList);

    MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();

    HttpHeaders headers = restHelper.getHeaders();
    headers.setContentType(MediaType.MULTIPART_FORM_DATA);
    body.add("file", new FileSystemResource(tmpFile));

    HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
    String serverUrl = restHelper.toUrl(Mapping.URL_CLASSIFICATIONDEFINITIONS);

    return template.postForEntity(serverUrl, requestEntity, Void.class);
  }
}
