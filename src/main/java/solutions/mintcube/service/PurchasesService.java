package solutions.mintcube.service;

import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.text.MessageFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Service
public class PurchasesService {

    private static final Logger LOG = LogManager.getLogger(PurchasesService.class);

    private static final String ALL_USERS_PATTERN_PATH = "http://74.50.59.155:6000/api/users/{0}";
    private static final String PRODUCT_INFO_PATTERN_PATH = "http://74.50.59.155:6000/api/products/{0}";
    private static final String RECENT_BY_USER_PATTERN_PATH = "http://74.50.59.155:6000/api/purchases/by_user/{0}?limit=5";
    private static final String RECENT_BY_PRODUCT_PATTERN_PATH = "http://74.50.59.155:6000/api/purchases/by_product/{0}";

    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(10);

    @Cacheable("users")
    public boolean isUserRegistered(String username) throws UnirestException {

        String apiPath = MessageFormat.format(ALL_USERS_PATTERN_PATH, username);
        HttpResponse<JsonNode> user = Unirest.get(apiPath).asJson();
        JsonNode userBody = user.getBody();

        //Make sure the API response contains just a user for the received username
        if (user.getStatus() == HttpStatus.OK.value() && !userBody.isArray() && userBody.getObject().has("user")
                && StringUtils.equals(userBody.getObject().getJSONObject("user").getString("username"), username))
            return true;

        return false;
    }

    public JSONArray getRecentPurchasesForUsername(String username) throws UnirestException {

        String apiPath = MessageFormat.format(RECENT_BY_USER_PATTERN_PATH, username);
        HttpResponse<JsonNode> recentPurchasesRaw = Unirest.get(apiPath).asJson();
        JSONObject recentPurchasesBody = recentPurchasesRaw.getBody().getObject();

        //In case the user has no purchases stop the process and return an empty array
        if (!recentPurchasesBody.has("purchases")
                || recentPurchasesBody.getJSONArray("purchases").length() == 0) {
            return new JSONArray();
        }

        //Grab the date about products and buyers and aggregate the response

        JSONArray response = new JSONArray();

        HashMap<JSONObject, Collection<String>> productInfoAndBuyers = getProductInfoAndBuyers(recentPurchasesBody);
        final HashMap<JSONObject, Collection<String>> sortedProductInfoAndBuyers = productInfoAndBuyers.entrySet().stream()
                .sorted((o1, o2) -> Integer.compare(o2.getValue().size(), o1.getValue().size()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (x,y)-> {throw new AssertionError();}, LinkedHashMap::new));

        sortedProductInfoAndBuyers.keySet().forEach(product -> {
            JSONObject resultElement = product.getJSONObject("product");
            resultElement.put("recent", sortedProductInfoAndBuyers.get(product));

            response.put(resultElement);
        });
        return response;
    }

    @Cacheable("products")
    private JSONObject getProductInfo(Integer productId) throws UnirestException {
        String apiPath = MessageFormat.format(PRODUCT_INFO_PATTERN_PATH, Integer.toString(productId));
        return Unirest.get(apiPath).asJson().getBody().getObject();
    }

    private Collection<String> getBuyersForProduct(Integer productId) throws UnirestException {
        String apiPath = MessageFormat.format(RECENT_BY_PRODUCT_PATTERN_PATH, Integer.toString(productId));
        JSONObject buyersBody = Unirest.get(apiPath).asJson().getBody().getObject();

        Set<String> buyers = new HashSet<>();

        if (!buyersBody.has("purchases") || buyersBody.getJSONArray("purchases").length() == 0)
            return buyers;

        JSONArray buyersJson = buyersBody.getJSONArray("purchases");
        for(int i = 0; i < buyersJson.length(); i++)
            buyers.add(buyersJson.getJSONObject(i).getString("username"));

        return buyers;
    }

    private HashMap<JSONObject,Collection<String>> getProductInfoAndBuyers(JSONObject recentPurchasesBody) {

        Set<Integer> productIds = new HashSet<>();
        List<Future<Void>> futures = new LinkedList<>();

        HashMap<JSONObject, Collection<String>> productInfoAndBuyers = new LinkedHashMap<>();

        JSONArray recentPurchases = recentPurchasesBody.getJSONArray("purchases");
        for (int i = 0; i < recentPurchases.length(); i++) {
            JSONObject recentPurchase = recentPurchases.getJSONObject(i);
            if (!recentPurchase.has("productId"))
                continue;

            Integer productId = recentPurchase.getInt("productId");
            if (productIds.contains(productId))
                continue;

            productIds.add(productId);
            futures.add(EXECUTOR.submit(() -> {
                productInfoAndBuyers.put(getProductInfo(productId), getBuyersForProduct(productId));
                return null;
            }));

        }

        futures.forEach(future -> {
            try {
                future.get();
            } catch (InterruptedException | ExecutionException e) {
                LOG.error(e.getMessage());
            }
        });
        return productInfoAndBuyers;
    }
}
