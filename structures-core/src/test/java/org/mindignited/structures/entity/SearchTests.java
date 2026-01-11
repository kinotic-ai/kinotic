

package org.mindignited.structures.entity;

import org.junit.jupiter.api.extension.ExtendWith;
import org.mindignited.structures.ElasticsearchTestBase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@SpringBootTest
public class SearchTests extends ElasticsearchTestBase {

//    @Autowired
//    private ItemServiceInternal itemService;
//    @Autowired
//    private TraitService traitService;
//    @Autowired
//    private StructureServiceInternal structureService;
//    @Autowired
//    private StructureTestHelper structureTestHelper;
//
//
//    @Test
//    public void tryCreateFiveItemsAndGetAll() throws Exception {
//
//        Structure structure = structureTestHelper.getSimpleItemStructure();
//
//        int index = 0;
//        while (index < 5) {
//
//            // now we can create an item with the above fields
//            TypeCheckMap obj = new TypeCheckMap();
//            obj.put("vpnIp", "10.0." + index + ".11");
//            obj.put("ip", "192.0." + index + ".11");
//            obj.put("mac", "00000000000" + index);
//
//            TypeCheckMap saved = itemService.upsertItem(structure.getId(), obj, null);
//
//            Thread.sleep(1000);// give time for ES to flush the new item
//
//            index++;
//        }
//
//
//        final SearchHits hits = itemService.getAll(structure.getId(), 100, 0, null);
//
//        for (SearchHit item : hits.getHits()) {
//            if (item != null) {
//                itemService.delete(structure.getId(), item.getId(), null);
//
//                Thread.sleep(1000);
//            }
//
//        }
//
//        structureService.delete(structure.getId());
//
//        if (hits.getTotalHits().value != 5) {
//            throw new IllegalStateException("Added 5 Items and expected 5 items returned by getAll() - got " + hits.getTotalHits().value);
//        }
//
//
//    }
//
//    @Test
//    public void createFiveItemsAndSearchExact() throws Exception {
//
//        Structure structure = structureTestHelper.getSimpleItemStructure();
//
//        int index = 0;
//        while (index < 5) {
//
//            // now we can create an item with the above fields
//            TypeCheckMap obj = new TypeCheckMap();
//            obj.put("ip", "192.0." + index + ".11");
//            obj.put("mac", "00000000000" + index);
//
//            TypeCheckMap saved = itemService.upsertItem(structure.getId(), obj, null);
//
//            Thread.sleep(1000);// give time for ES to flush the new item
//
//            index++;
//        }
//
//
//        final SearchHits zeroDotEleven = itemService.searchTerms(structure.getId(), 100, 0, "ip", null, "192.0.0.11");
//
//        final SearchHits twoMacs = itemService.searchTerms(structure.getId(), 100, 0, "mac", null, "000000000000", "000000000001");
//
//        final SearchHits all = itemService.getAll(structure.getId(), 100, 0, null);
//
//        for (SearchHit item : all.getHits()) {
//            if (item != null) {
//                itemService.delete(structure.getId(), item.getId(), null);
//
//                Thread.sleep(1000);
//            }
//
//        }
//
//
//        structureService.delete(structure.getId());
//
//        if (zeroDotEleven.getTotalHits().value != 1) {
//            throw new IllegalStateException("Queried IP = 192.0.0.11, expected 1 but got " + zeroDotEleven.getTotalHits().value);
//        }
//
//
//        if (twoMacs.getTotalHits().value != 2) {
//            throw new IllegalStateException("Queried MAC = 000000000000 and 000000000001, expected 2 but got " + twoMacs.getTotalHits().value);
//        }
//
//
//        if (all.getTotalHits().value != 5) {
//            throw new IllegalStateException("Added 5 Items and expected 5 items returned by getAll() - got " + all.getTotalHits().value);
//        }
//
//    }
//
//    @Test
//    public void createFiveItemsAndSearchForText() throws Exception {
//
//        Structure structure = structureTestHelper.getSimpleItemStructure();
//
//        int index = 0;
//        while (index < 5) {
//
//            // now we can create an item with the above fields
//            TypeCheckMap obj = new TypeCheckMap();
//            if(index % 2 == 0) obj.put("vpnIp", "172.16." + index + ".11");
//            obj.put("ip", "192.0." + index + ".11");
//            obj.put("mac", "00000000000" + index);
//            if (index == 0) {
//                obj.put("description", "zero one two three four five six seven eight nine ten");
//            } else if (index == 1) {
//                obj.put("description", "ten eleven twelve thirteen fourteen fifteen sixteen seventeen eighteen nineteen twenty");
//            } else if (index == 2) {
//                obj.put("description", "twenty twenty-one twenty-two twenty-three twenty-four twenty-five twenty-six twenty-seven twenty-eight twenty-nine thirty");
//            } else if (index == 3) {
//                obj.put("description", "thirty thirty-one thirty-two thirty-three thirty-four thirty-five thirty-six thirty-seven thirty-eight thirty-nine forty");
//            } else if (index == 4) {
//                obj.put("description", "forty forty-one forty-two forty-three forty-four forty-five forty-six forty-seven forty-eight forty-nine fifty");
//            }
//
//
//            TypeCheckMap saved = itemService.upsertItem(structure.getId(), obj, null);
//
//            Thread.sleep(1000);// give time for ES to flush the new item
//
//            index++;
//        }
//
//
//        final SearchHits oneItemUsingText = itemService.searchFullText(structure.getId(), 100, 0, "zero", null, "description");
//
//        final SearchHits twoItemsUsingOr = itemService.searchFullText(structure.getId(), 100, 0, "zero|fifty", null, "description");
//
//        final SearchHits twoItemsUsingSameText = itemService.searchFullText(structure.getId(), 100, 0, "twenty", null, "description");
//
//        final SearchHits all = itemService.getAll(structure.getId(), 100, 0, null);
//
//        for (SearchHit item : all.getHits()) {
//            if (item != null) {
//                itemService.delete(structure.getId(), item.getId(), null);
//
//                Thread.sleep(1000);
//            }
//
//        }
//
//
//        structureService.delete(structure.getId());
//
//        if (oneItemUsingText.getTotalHits().value != 1) {
//            throw new IllegalStateException("Queried Description and zero, expected 1 but got " + oneItemUsingText.getTotalHits().value);
//        }
//
//
//        if (twoItemsUsingOr.getTotalHits().value != 2) {
//            throw new IllegalStateException("Queried Description and zero|fifty, expected 2 but got " + twoItemsUsingOr.getTotalHits().value);
//        }
//
//
//        if (twoItemsUsingSameText.getTotalHits().value != 2) {
//            throw new IllegalStateException("Queried Description and twenty, expected 2 but got " + twoItemsUsingSameText.getTotalHits().value);
//        }
//
//
//        if (all.getTotalHits().value != 5) {
//            throw new IllegalStateException("Added 5 Items and expected 5 items returned by getAll() - got " + all.getTotalHits().value);
//        }
//
//    }
//
//    @Test
//    public void createFiveItemsAndSearchUsingLuceneSyntax() throws Exception {
//
//        Structure structure = structureTestHelper.getSimpleItemStructure();
//
//        int index = 0;
//        while (index < 5) {
//
//            // now we can create an item with the above fields
//            TypeCheckMap obj = new TypeCheckMap();
//            obj.put("vpnIp", "172.16." + index + ".11");
//            obj.put("ip", "192.0." + index + ".11");
//            obj.put("mac", "00000000000" + index);
//            if (index == 0) {
//                obj.put("description", "zero one two three four five six seven eight nine ten");
//            } else if (index == 1) {
//                obj.put("description", "ten eleven twelve thirteen fourteen fifteen sixteen seventeen eighteen nineteen twenty");
//            } else if (index == 2) {
//                obj.put("description", "twenty twenty-one twenty-two twenty-three twenty-four twenty-five twenty-six twenty-seven twenty-eight twenty-nine thirty");
//            } else if (index == 3) {
//                obj.put("description", "thirty thirty-one thirty-two thirty-three thirty-four thirty-five thirty-six thirty-seven thirty-eight thirty-nine forty");
//            } else if (index == 4) {
//                obj.put("description", "forty forty-one forty-two forty-three forty-four forty-five forty-six forty-seven forty-eight forty-nine fifty");
//            }
//
//
//            TypeCheckMap saved = itemService.upsertItem(structure.getId(), obj, null);
//
//            Thread.sleep(1000);// give time for ES to flush the new item
//
//            index++;
//        }
//
//
//        SearchHits oneRetured = itemService.search(structure.getId(), "(ip:192.0.0.11 OR ip:192.0.1.11) AND (mac:000000000003 OR mac:000000000000)", 100, 0, null);
//
//        final SearchHits all = itemService.getAll(structure.getId(), 100, 0, null);
//
//        for (SearchHit item : all.getHits()) {
//            if (item != null) {
//                itemService.delete(structure.getId(), item.getId(), null);
//
//                Thread.sleep(1000);
//            }
//
//        }
//
//
//        structureService.delete(structure.getId());
//
//        if (oneRetured.getTotalHits().value != 1) {
//            throw new IllegalStateException("Queried ((ip:192.0.0.11 OR ip:192.0.1.11) AND (mac:000000000003 OR mac:000000000000)), expected 1 - got " + oneRetured.getTotalHits().value);
//        }
//
//
//        if (all.getTotalHits().value != 5) {
//            throw new IllegalStateException("Added 5 Items and expected 5 items returned by getAll() - got " + all.getTotalHits().value);
//        }
//
//    }
//
//    @Test
//    public void tryCreate5ItemsThenDelete2AndPerformGetAll() throws Exception {
//
//        Structure structure = structureTestHelper.getSimpleItemStructure();
//
//        int index = 0;
//        String delete1Id = "";
//        String delete2Id = "";
//        while (index < 5) {
//
//            // now we can create an item with the above fields
//            TypeCheckMap obj = new TypeCheckMap();
//            obj.put("ip", "192.0." + index + ".11");
//            obj.put("mac", "00000000000" + index);
//
//            TypeCheckMap saved = itemService.upsertItem(structure.getId(), obj, null);
//
//            if (index == 1) {
//                delete1Id = saved.getString("id");
//            } else if (index == 3) {
//                delete2Id = saved.getString("id");
//            }
//
//
//            Thread.sleep(1000);// give time for ES to flush the new item
//
//            index++;
//        }
//
//
//        itemService.delete(structure.getId(), delete1Id, null);
//        itemService.delete(structure.getId(), delete2Id, null);
//        Thread.sleep(1000);
//
//        final SearchHits hits = itemService.getAll(structure.getId(), 100, 0, null);
//
//        for (SearchHit item : hits.getHits()) {
//            if (item != null) {
//                itemService.delete(structure.getId(), item.getId(), null);
//
//                Thread.sleep(1000);
//            }
//
//        }
//
//
//        structureService.delete(structure.getId());
//
//        if (hits.getTotalHits().value != 3) {
//            throw new IllegalStateException("Added 5 Items, then deleted 2. Expected 3 items returned by getAll() - got " + hits.getTotalHits().value);
//        }
//
//    }
//
//    @Test
//    public void tryCreate5ItemsThenDelete2AndPerformSearchExact() throws Exception {
//
//        Structure structure = structureTestHelper.getSimpleItemStructure();
//
//        int index = 0;
//        String delete1Id = "";
//        String delete2Id = "";
//        while (index < 5) {
//
//            // now we can create an item with the above fields
//            TypeCheckMap obj = new TypeCheckMap();
//            obj.put("vpnIp", "172.16." + index + ".11");
//            obj.put("ip", "192.0." + index + ".11");
//            obj.put("mac", "00000000000" + index);
//
//            TypeCheckMap saved = itemService.upsertItem(structure.getId(), obj, null);
//
//            if (index == 1) {
//                delete1Id = saved.getString("id");
//            } else if (index == 3) {
//                delete2Id = saved.getString("id");
//            }
//
//
//            Thread.sleep(1000);// give time for ES to flush the new item
//
//            index++;
//        }
//
//
//        itemService.delete(structure.getId(), delete1Id, null);
//        itemService.delete(structure.getId(), delete2Id, null);
//        Thread.sleep(1000);
//
//        final SearchHits oneDotEleven = itemService.searchTerms(structure.getId(), 100, 0, "ip", null, "192.0.1.11");
//
//        final SearchHits oneMacs = itemService.searchTerms(structure.getId(), 100, 0, "mac", null, "000000000003", "000000000004");
//
//        final SearchHits all = itemService.getAll(structure.getId(), 100, 0, null);
//
//        for (SearchHit item : all.getHits()) {
//            if (item != null) {
//                itemService.delete(structure.getId(), item.getId(), null);
//
//                Thread.sleep(1000);
//            }
//
//        }
//
//
//        structureService.delete(structure.getId());
//
//        if (oneDotEleven.getTotalHits().value != 0) {
//            throw new IllegalStateException("Queried IP = 192.0.1.11, was deleted and expected 0 but got " + oneDotEleven.getTotalHits().value);
//        }
//
//
//        if (oneMacs.getTotalHits().value != 1) {
//            throw new IllegalStateException("Queried MAC = 000000000003 and 000000000004, 3 was deleted and so expected 1 but got " + oneMacs.getTotalHits().value);
//        }
//
//
//        if (all.getTotalHits().value != 3) {
//            throw new IllegalStateException("Added 5 Items, then deleted 2. Expected 3 items returned by getAll() - got " + all.getTotalHits().value);
//        }
//
//    }
//
//    @Test
//    public void tryCreate5ItemsThenDelete2AndPerformSearchText() throws Exception {
//
//        Structure structure = structureTestHelper.getSimpleItemStructure();
//
//        int index = 0;
//        String delete1Id = "";
//        String delete2Id = "";
//        while (index < 5) {
//
//            //NOTE: when ES destructures the data, it must break at - and space.. so that thrity-one becomes thirty and one
//            // if you search over thirteen you get what you expect, but this is just how to structure the query against
//            // the data.
//
//            // now we can create an item with the above fields
//            TypeCheckMap obj = new TypeCheckMap();
//            obj.put("ip", "192.0." + index + ".11");
//            obj.put("mac", "00000000000" + index);
//            if (index == 0) {
//                obj.put("description", "zero one two three four five six seven eight nine ten");
//            } else if (index == 1) {
//                obj.put("description", "ten eleven twelve thirteen fourteen fifteen sixteen seventeen eighteen nineteen twenty");
//            } else if (index == 2) {
//                obj.put("description", "twenty twenty-one twenty-two twenty-three twenty-four twenty-five twenty-six twenty-seven twenty-eight twenty-nine thirty");
//            } else if (index == 3) {
//                obj.put("description", "thirty thirty-one thirty-two thirty-three thirty-four thirty-five thirty-six thirty-seven thirty-eight thirty-nine forty");
//            } else if (index == 4) {
//                obj.put("description", "forty forty-one forty-two forty-three forty-four forty-five forty-six forty-seven forty-eight forty-nine fifty");
//            }
//
//
//            TypeCheckMap saved = itemService.upsertItem(structure.getId(), obj, null);
//
//            if (index == 1) {
//                delete1Id = saved.getString("id");
//            } else if (index == 3) {
//                delete2Id = saved.getString("id");
//            }
//
//
//            Thread.sleep(1000);// give time for ES to flush the new item
//
//            index++;
//        }
//
//
//        itemService.delete(structure.getId(), delete1Id, null);
//        itemService.delete(structure.getId(), delete2Id, null);
//        Thread.sleep(2000);
//
//        final SearchHits zeroItemUsingText = itemService.searchFullText(structure.getId(), 100, 0, "thirteen", null, "description");
//
//        final SearchHits oneItemsUsingSameText = itemService.searchFullText(structure.getId(), 100, 0, "twenty", null, "description");
//
//        final SearchHits oneItemsUsingOr = itemService.searchFullText(structure.getId(), 100, 0, "thirty|thirteen", null, "description");
//
//        final SearchHits all = itemService.getAll(structure.getId(), 100, 0, null);
//
//        for (SearchHit item : all.getHits()) {
//            if (item != null) {
//                itemService.delete(structure.getId(), item.getId(), null);
//
//                Thread.sleep(1000);
//            }
//
//        }
//
//
//        structureService.delete(structure.getId());
//
//        if (zeroItemUsingText.getTotalHits().value != 0) {
//            throw new IllegalStateException("Queried Description and thirty-one, but was deleted, expected 0 but got " + zeroItemUsingText.getTotalHits().value);
//        }
//
//
//        if (oneItemsUsingSameText.getTotalHits().value != 1) {
//            throw new IllegalStateException("Queried Description and twenty, one deleted, expected 1 but got " + oneItemsUsingSameText.getTotalHits().value);
//        }
//
//
//        if (oneItemsUsingOr.getTotalHits().value != 1) {
//            throw new IllegalStateException("Queried Description and twenty|thirty-one, one was deleted, expected 1 but got " + oneItemsUsingOr.getTotalHits().value);
//        }
//
//
//        if (all.getTotalHits().value != 3) {
//            throw new IllegalStateException("Added 5 Items, then deleted 2. Expected 3 items returned by getAll() - got " + all.getTotalHits().value);
//        }
//
//    }
//
//    @Test
//    public void tryCreate5ItemsThenDelete2AndPerformSearchLucene() throws Exception {
//
//        Structure structure = structureTestHelper.getSimpleItemStructure();
//
//        int index = 0;
//        String delete1Id = "";
//        String delete2Id = "";
//        while (index < 5) {
//
//            // now we can create an item with the above fields
//            TypeCheckMap obj = new TypeCheckMap();
//            obj.put("vpnIp", "10.0." + index + ".11");
//            obj.put("ip", "192.0." + index + ".11");
//            obj.put("mac", "00000000000" + index);
//            if (index == 0) {
//                obj.put("description", "zero one two three four five six seven eight nine ten");
//            } else if (index == 1) {
//                obj.put("description", "ten eleven twelve thirteen fourteen fifteen sixteen seventeen eighteen nineteen twenty");
//            } else if (index == 2) {
//                obj.put("description", "twenty twenty-one twenty-two twenty-three twenty-four twenty-five twenty-six twenty-seven twenty-eight twenty-nine thirty");
//            } else if (index == 3) {
//                obj.put("description", "thirty thirty-one thirty-two thirty-three thirty-four thirty-five thirty-six thirty-seven thirty-eight thirty-nine forty");
//            } else if (index == 4) {
//                obj.put("description", "forty forty-one forty-two forty-three forty-four forty-five forty-six forty-seven forty-eight forty-nine fifty");
//            }
//
//
//            TypeCheckMap saved = itemService.upsertItem(structure.getId(), obj, null);
//
//            if (index == 1) {
//                delete1Id = saved.getString("id");
//            } else if (index == 3) {
//                delete2Id = saved.getString("id");
//            }
//
//
//            Thread.sleep(1000);// give time for ES to flush the new item
//
//            index++;
//        }
//
//
//        itemService.delete(structure.getId(), delete1Id, null);
//        itemService.delete(structure.getId(), delete2Id, null);
//        Thread.sleep(1000);
//
//        final SearchHits zeroRetured = itemService.search(structure.getId(), "(ip:192.0.1.11 OR ip:192.0.3.11) AND (mac:000000000001 OR mac:000000000003)", 100, 0, null);
//
//        final SearchHits all = itemService.getAll(structure.getId(), 100, 0, null);
//
//        for (SearchHit item : all.getHits()) {
//            if (item != null) {
//                itemService.delete(structure.getId(), item.getId(), null);
//
//                Thread.sleep(1000);
//            }
//
//        }
//
//
//        structureService.delete(structure.getId());
//
//        if (zeroRetured.getTotalHits().value != 0) {
//            throw new IllegalStateException("Queried ((ip:192.0.1.11 OR ip:192.0.3.11) AND (mac:000000000001 OR mac:000000000003)), both were deleted, expected 0 - got " + zeroRetured.getTotalHits().value);
//        }
//
//
//        if (all.getTotalHits().value != 3) {
//            throw new IllegalStateException("Added 5 Items, then deleted 2. Expected 3 items returned by getAll() - got " + all.getTotalHits().value);
//        }
//
//    }

}
