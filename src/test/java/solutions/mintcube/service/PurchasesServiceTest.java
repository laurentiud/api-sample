package solutions.mintcube.service;

import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(PurchasesService.class)
public class PurchasesServiceTest {

    private String existingUsername;

    @Autowired
    private PurchasesService purchasesService;

    @Before
    public void getAllUsers() throws UnirestException {
        JsonNode allUsersJson = Unirest.get("http://74.50.59.155:6000/api/users").asJson().getBody();
        existingUsername = allUsersJson.getObject().getJSONArray("users").getJSONObject(0).getString("username");
    }

    @Test
    public void testRegisteredUsernameIsFound() throws UnirestException {
        Assert.assertTrue(purchasesService.isUserRegistered(existingUsername));
    }

    @Test
    public void testInexistentUsernameIsNotFound() throws UnirestException {
        Assert.assertFalse(purchasesService.isUserRegistered("unlikelyUsername2016forUnitTestsUsage"));
    }
}
