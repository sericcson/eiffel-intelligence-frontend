/*
   Copyright 2017 Ericsson AB.
   For a full list of individual contributors, please see the commit history.
   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at
       http://www.apache.org/licenses/LICENSE-2.0
   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/
package com.ericsson.ei.frontend;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.io.BufferedReader;
import java.io.FileReader;
import java.net.URI;
import java.util.List;
import java.util.stream.Stream;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import static org.junit.Assert.assertEquals;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.ericsson.ei.frontend.model.BackEndInformation;
import com.ericsson.ei.frontend.utils.BackEndInformationControllerUtils;
import com.ericsson.ei.frontend.utils.BackEndInstancesUtils;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest
@AutoConfigureMockMvc
public class BackendInformationControllerUtilsTest {

    private static final String BACKEND_INSTANCE_FILE_PATH = "src/test/resources/backendInstances/backendInstance.json";
    private static final String BACKEND_INSTANCES_FILE_PATH = "src/test/resources/backendInstances/backendInstances.json";

    @MockBean
    private BackEndInstancesUtils backEndInstancesUtils;

    @Autowired
    private BackEndInformationControllerUtils backendInfoContrUtils;

    private JsonObject instance;
    private JsonArray instances;
    private JsonArray instancesWithActive;
    private JsonArray instancesWithNotDefaultActive;
    private List<BackEndInformation> information;

    private HttpServletRequest mockedRequest;
    private HttpSession mockedSession;
    private BufferedReader mockedReader;
    private Stream<String> stream;

    @Before
    public void before() throws Exception {
        instance = new JsonParser().parse(new FileReader(BACKEND_INSTANCE_FILE_PATH)).getAsJsonObject();
        instances = new JsonParser().parse(new FileReader(BACKEND_INSTANCES_FILE_PATH)).getAsJsonArray();

        mockedRequest = Mockito.mock(HttpServletRequest.class);
        mockedSession = Mockito.mock(HttpSession.class);
        mockedReader = Mockito.mock(BufferedReader.class);
        stream = Mockito.mock(Stream.class);

        when(mockedRequest.getSession()).thenReturn(mockedSession);
        when(mockedRequest.getReader()).thenReturn(mockedReader);
        when(mockedReader.lines()).thenReturn(stream);

        when(mockedRequest.getMethod()).thenReturn("Test");
        when(backEndInstancesUtils.getBackEndsAsJsonArray()).thenReturn(instances);
    }

    @Test
    public void testHandleRequestForInstances() throws Exception {
        ResponseEntity<String> response;
        ResponseEntity<String> expectedResponse;

        expectedResponse = createExpectedResponse(instances.toString(), HttpStatus.OK);
        response = backendInfoContrUtils.handleRequestForInstances(mockedRequest);
        assertEquals(expectedResponse, response);
    }

    @Test
    public void testHandleRequestToSwitchBackEnd() throws Exception {
        ResponseEntity<String> response;
        ResponseEntity<String> expectedResponse;

        // Test name is given.
        when(stream.collect(any())).thenReturn("TestName");
        expectedResponse = createExpectedResponse(
                "{\"message\": \"Backend instance with name 'TestName' was selected.\"}".toString(), HttpStatus.OK);
        response = backendInfoContrUtils.handleRequestToSwitchBackEnd(mockedRequest);
        assertEquals(expectedResponse, response);
    }

    @Test
    public void testHandleRequestToDeleteBackEnd() throws Exception {
        ResponseEntity<String> response;
        ResponseEntity<String> expectedResponse;

        when(stream.collect(any())).thenReturn(instance.toString());
        expectedResponse = createExpectedResponse(
                "{\"message\": \"Backend instance with name 'someName' was deleted.\"}".toString(), HttpStatus.OK);
        response = backendInfoContrUtils.handleRequestToDeleteBackEnd(mockedRequest);
        assertEquals(expectedResponse, response);
    }

    @Test
    public void testHandleRequestToAddBackEnd() throws Exception {
        when(stream.collect(any())).thenReturn(instance.toString());
        ResponseEntity<String> response;
        ResponseEntity<String> expectedResponse;

        // Test successfully added.
        when(backEndInstancesUtils.checkIfInstanceNameAlreadyExist(any())).thenReturn(false);
        when(backEndInstancesUtils.checkIfInstanceAlreadyExist(any())).thenReturn(false);
        when(backEndInstancesUtils.hasRequiredJsonKeys(any())).thenReturn(true);
        expectedResponse = createExpectedResponse(
                "{\"message\": \"Back-end instance with name 'someName' was successfully added to the back-end instance list.\"}",
                HttpStatus.OK);
        response = backendInfoContrUtils.handleRequestToAddBackEnd(mockedRequest);
        assertEquals(expectedResponse, response);

        // Test with missing keys
        when(backEndInstancesUtils.hasRequiredJsonKeys(any())).thenReturn(false);
        expectedResponse = createExpectedResponse(
                "{\"message\": \"Back-end instance is missing required data.\"}",
                HttpStatus.BAD_REQUEST);
        response = backendInfoContrUtils.handleRequestToAddBackEnd(mockedRequest);
        assertEquals(expectedResponse, response);

        // Test with null values
        when(backEndInstancesUtils.hasRequiredJsonKeys(any())).thenReturn(true);
        when(backEndInstancesUtils.containsNullValues(any())).thenReturn(true);
        expectedResponse = createExpectedResponse(
                "{\"message\": \"Back-end instance can not have null values.\"}",
                HttpStatus.BAD_REQUEST);
        response = backendInfoContrUtils.handleRequestToAddBackEnd(mockedRequest);
        assertEquals(expectedResponse, response);

        // Test with additional unrecognized keys
        when(backEndInstancesUtils.hasRequiredJsonKeys(any())).thenReturn(true);
        when(backEndInstancesUtils.containsNullValues(any())).thenReturn(false);
        when(backEndInstancesUtils.containsAdditionalKeys(any())).thenReturn(true);
        expectedResponse = createExpectedResponse(
                "{\"message\": \"Back-end instance contains unrecognized JSON keys.\"}",
                HttpStatus.BAD_REQUEST);
        response = backendInfoContrUtils.handleRequestToAddBackEnd(mockedRequest);
        assertEquals(expectedResponse, response);

        // Test back end name already exist
        when(backEndInstancesUtils.hasRequiredJsonKeys(any())).thenReturn(true);
        when(backEndInstancesUtils.containsNullValues(any())).thenReturn(false);
        when(backEndInstancesUtils.containsAdditionalKeys(any())).thenReturn(false);
        when(backEndInstancesUtils.checkIfInstanceNameAlreadyExist(any())).thenReturn(true);
        expectedResponse = createExpectedResponse(
                "{\"message\": \"Back-end instance with name 'someName' already exists.\"}", HttpStatus.BAD_REQUEST);
        response = backendInfoContrUtils.handleRequestToAddBackEnd(mockedRequest);
        assertEquals(expectedResponse, response);

        // Test instance already exist
        when(backEndInstancesUtils.hasRequiredJsonKeys(any())).thenReturn(true);
        when(backEndInstancesUtils.containsNullValues(any())).thenReturn(false);
        when(backEndInstancesUtils.containsAdditionalKeys(any())).thenReturn(false);
        when(backEndInstancesUtils.checkIfInstanceNameAlreadyExist(any())).thenReturn(false);
        when(backEndInstancesUtils.checkIfInstanceAlreadyExist(any())).thenReturn(true);
        expectedResponse = createExpectedResponse("{\"message\": \"Back-end instance with given values already exist.\"}",
                HttpStatus.BAD_REQUEST);
        response = backendInfoContrUtils.handleRequestToAddBackEnd(mockedRequest);
        assertEquals(expectedResponse, response);

        // Test failure to add new default instance.
        when(backEndInstancesUtils.hasRequiredJsonKeys(any())).thenReturn(true);
        when(backEndInstancesUtils.containsNullValues(any())).thenReturn(false);
        when(backEndInstancesUtils.containsAdditionalKeys(any())).thenReturn(false);
        when(backEndInstancesUtils.checkIfInstanceNameAlreadyExist(any())).thenReturn(false);
        when(backEndInstancesUtils.checkIfInstanceAlreadyExist(any())).thenReturn(false);
        when(backEndInstancesUtils.hasDefaultBackend()).thenReturn(true);
        instance.addProperty("defaultBackend", true);
        when(stream.collect(any())).thenReturn(instance.toString());
        expectedResponse = createExpectedResponse("{\"message\": \"A default back-end instance already exists.\"}",
                HttpStatus.BAD_REQUEST);
        response = backendInfoContrUtils.handleRequestToAddBackEnd(mockedRequest);
        assertEquals(expectedResponse, response);
        instance.addProperty("defaultBackend", false);

        // Test internal error
        when(stream.collect(any())).thenReturn("[");
        expectedResponse = createExpectedResponse(
                "{\"message\": \"Internal Error: java.io.EOFException: End of input at line 1 column 2 path $[0]\"}",
                HttpStatus.INTERNAL_SERVER_ERROR);
        response = backendInfoContrUtils.handleRequestToAddBackEnd(mockedRequest);
        assertEquals(expectedResponse, response);

    }

    private ResponseEntity<String> createExpectedResponse(String response, HttpStatus status) {
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(MediaType.APPLICATION_JSON);
        httpHeaders.setLocation(URI.create("/"));
        ResponseEntity<String> expectedResponse = new ResponseEntity<>(response, httpHeaders, status);
        return expectedResponse;
    }

}
