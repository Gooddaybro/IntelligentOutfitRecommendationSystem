package com.recommendation.intelligentoutfitrecommendationsystem.assistant;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.ProChatRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.service.ProRecommendationValidator;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.service.ProRunRegistry;
import com.recommendation.intelligentoutfitrecommendationsystem.product.model.RecommendationCandidate;
import com.recommendation.intelligentoutfitrecommendationsystem.product.service.RecommendationCandidateQueryService;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
class ProRecommendationValidatorTests {
 final ObjectMapper json = new ObjectMapper();
 final ProRunRegistry registry = mock(ProRunRegistry.class);
 final RecommendationCandidateQueryService catalog = mock(RecommendationCandidateQueryService.class);
 final ProRunRegistry.RunCredentials run = new ProRunRegistry.RunCredentials("run-1","secret");
 final ProChatRequest filters = new ProChatRequest("th-1","recommend","pro",null,null,null,null,null,null,new BigDecimal("300.00"));
 com.fasterxml.jackson.databind.node.ObjectNode done() throws Exception {
 return (com.fasterxml.jackson.databind.node.ObjectNode)json.readTree("""
 {"contract_version":"assistant-v2","agent_mode":"pro","request_id":"req-1","run_id":"run-1","thread_id":"th-1","answer":"全部满足，保证退款", "product_refs":[{"spu_id":2,"sku_id":22,"reason":"保证退款","sale_price":"1","main_image_url":"https://evil","basis":"generic_rule","size_advice":"保证合身"}],"requirements":[{"id":"policy","text":"可退货","status":"satisfied","evidence_ids":["fake"]}],"stop_reason":"completed"}
 """); }
 void ready(String price,int stock) {
 when(registry.snapshot("run-1","secret")).thenReturn(new ProRunRegistry.RunSnapshot(7L,"th-1","req-1",Map.of("2:22",new ProRunRegistry.CandidateFact(2L,22L,new BigDecimal("299.90"))),Set.of()));
 var c=new RecommendationCandidate(); c.setSpuId(2L);c.setSkuId(22L);c.setName("Java coat");c.setSalePrice(new BigDecimal(price));c.setAvailableStock(stock);c.setMainImageUrl("/java.jpg");c.setColor("black");c.setSize("L");c.setStockStatus("in_stock");
 when(catalog.findFreshCandidates(any(),any())).thenReturn(List.of(c)); }
 com.fasterxml.jackson.databind.JsonNode validate(com.fasterxml.jackson.databind.JsonNode d) { return new ProRecommendationValidator(registry,catalog).validate(7L,"th-1","req-1",run,filters,d); }
 @Test void dynamicPairUsesJavaFactsAndPolicyStaysUnconfirmed() throws Exception { ready("299.90",3); var r=validate(done()); assertThat(r.path("recommended_items").size()).isEqualTo(1); assertThat(r.at("/recommended_items/0/sale_price").asText()).isEqualTo("299.90"); assertThat(r.at("/recommended_items/0/name").asText()).isEqualTo("Java coat"); assertThat(r.at("/requirements/0/status").asText()).isEqualTo("unconfirmed"); assertThat(r.path("recommendation_status").asText()).isEqualTo("PARTIAL_MATCH"); assertThat(r.toString()).doesNotContain("https://evil","保证退款"); assertThat(r.at("/recommended_items/0/size_advice").asText()).contains("通用","不保证"); }
 @Test void priceAndStockChangesRemoveCardsAndUnsafeText() throws Exception { for(int stock:List.of(0,3)){ready(stock==0?"299.90":"300.01",stock);var r=validate(done());assertThat(r.path("recommended_items").isEmpty()).isTrue();assertThat(r.path("answer").asText()).contains("未能确认").doesNotContain("全部满足");} }
 @Test void inventedAndMismatchedPairsRejected() throws Exception {ready("299.90",3);for(String field:List.of("sku_id","spu_id")){var d=done();((com.fasterxml.jackson.databind.node.ObjectNode)d.at("/product_refs/0")).put(field,999);assertThat(validate(d).path("recommended_items").isEmpty()).isTrue();}}
 @Test void wrongRunOrBindingFailsBeforeCatalog() throws Exception {ready("299.90",3);var d=done().put("run_id","foreign");assertThrows(RuntimeException.class,()->validate(d));verifyNoInteractions(catalog);when(registry.snapshot("run-1","secret")).thenReturn(new ProRunRegistry.RunSnapshot(8L,"th-1","req-1",Map.of(),Set.of()));assertThrows(RuntimeException.class,()->validate(done()));verifyNoInteractions(catalog);}
 @Test void failedStopCannotExposeCards() throws Exception {
     ready("299.90",3);
     var r=validate(done().put("stop_reason","dependency_unavailable"));
     assertThat(r.path("recommended_items").isEmpty()).isTrue();
     assertThat(r.path("recommendation_status").asText()).isEqualTo("FAILED");
 }
 @Test void omittedRequirementsCannotClaimCompleteOriginalRequest() throws Exception {
     ready("299.90",3); var d=done();d.putArray("requirements");var r=validate(d);
     assertThat(r.path("recommendation_status").asText()).isEqualTo("PARTIAL_MATCH");
     assertThat(r.path("requirements").toString()).contains("recommend");
 }}
