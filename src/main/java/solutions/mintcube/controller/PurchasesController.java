package solutions.mintcube.controller;

import com.mashape.unirest.http.exceptions.UnirestException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import solutions.mintcube.service.PurchasesService;

import javax.servlet.http.HttpServletRequest;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.web.bind.annotation.RequestMethod.GET;

@RestController
@RequestMapping("/api")
public class PurchasesController {

    private static final Logger LOG = LogManager.getLogger(PurchasesController.class);

    @Autowired
    private PurchasesService purchasesService;

    @RequestMapping(method = GET, value = "/recent_purchases/{username:.+}", produces = APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getRecentPurchasesForUsername(@PathVariable String username) throws UnirestException {

        if (!purchasesService.isUserRegistered(username))
            return new ResponseEntity<>("User with username of {" + username + "} was not found", HttpStatus.NOT_FOUND);

        JSONArray detailedRecentPurchases = purchasesService.getRecentPurchasesForUsername(username);
        return ResponseEntity.ok(detailedRecentPurchases.toString());
    }

    @ExceptionHandler
    public ResponseEntity<String> handleApiException(HttpServletRequest req, Exception e) {
        LOG.error(e);
        return new ResponseEntity<String>("Error encountered. Please contact support at abc@abc.abc", HttpStatus.INTERNAL_SERVER_ERROR);
    }
}