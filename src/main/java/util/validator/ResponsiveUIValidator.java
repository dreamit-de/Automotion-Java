package util.validator;

import http.helpers.Helper;
import io.appium.java_client.AppiumDriver;
import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.ParseException;
import org.openqa.selenium.*;
import util.driver.PageValidator;
import util.general.HtmlReportBuilder;
import util.validator.properties.Padding;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicLong;

import static environment.EnvironmentFactory.*;
import static util.general.SystemHelper.isRetinaDisplay;
import static util.validator.Constants.*;
import static util.validator.ResponsiveUIValidator.Units.PX;

public class ResponsiveUIValidator {
    static final int MIN_OFFSET = -10000;
    private final static Logger LOG = Logger.getLogger(ResponsiveUIValidator.class);
    protected static WebDriver driver;
    static WebElement rootElement;
    static long startTime;
    private static boolean isMobileTopBar = false;
    private static boolean withReport = false;
    private static String scenarioName = "Default";
    private static Color rootColor = new Color(255, 0, 0, 255);
    private static Color highlightedElementsColor = new Color(255, 0, 255, 255);
    private static Color linesColor = Color.ORANGE;
    private static String currentZoom = "100%";
    private static List<String> jsonFiles = new ArrayList<>();
    private static File screenshot;
    private static BufferedImage img;
    private static Graphics2D g;
    private static JSONArray errorMessage;
    boolean drawLeftOffsetLine = false;
    boolean drawRightOffsetLine = false;
    boolean drawTopOffsetLine = false;
    boolean drawBottomOffsetLine = false;
    String rootElementReadableName = "Root Element";
    List<WebElement> rootElements;
    ResponsiveUIValidator.Units units = PX;
    int pageWidth;
    int pageHeight;

    public ResponsiveUIValidator(WebDriver driver) {
        ResponsiveUIValidator.driver = driver;
        errorMessage = new JSONArray();
    }

    /**
     * Set color for main element. This color will be used for highlighting element in results
     *
     * @param color
     */
    public void setColorForRootElement(Color color) {
        rootColor = color;
    }

    /**
     * Set color for compared elements. This color will be used for highlighting elements in results
     *
     * @param color
     */
    public void setColorForHighlightedElements(Color color) {
        highlightedElementsColor = color;
    }

    /**
     * Set color for grid lines. This color will be used for the lines of alignment grid in results
     *
     * @param color
     */
    public void setLinesColor(Color color) {
        linesColor = color;
    }

    /**
     * Set top bar mobile offset. Applicable only for native mobile testing
     *
     * @param state
     */
    public void setTopBarMobileOffset(boolean state) {
        isMobileTopBar = state;
    }

    /**
     * Method that defines start of new validation. Needs to be called each time before calling findElement(), findElements()
     *
     * @return ResponsiveUIValidator
     */
    public ResponsiveUIValidator init() {
        return new ResponsiveUIValidator(driver);
    }

    /**
     * Method that defines start of new validation with specified name of scenario. Needs to be called each time before calling findElement(), findElements()
     *
     * @param scenarioName
     * @return ResponsiveUIValidator
     */
    public ResponsiveUIValidator init(String scenarioName) {
        ResponsiveUIValidator.scenarioName = scenarioName;
        return new ResponsiveUIValidator(driver);
    }

    /**
     * Main method to specify which element we want to validate (can be called only findElement() OR findElements() for single validation)
     *
     * @param element
     * @param readableNameOfElement
     * @return UIValidator
     */
    public UIValidator findElement(WebElement element, String readableNameOfElement) {
        return new UIValidator(driver, element, readableNameOfElement);
    }

    /**
     * Main method to specify the list of elements that we want to validate (can be called only findElement() OR findElements() for single validation)
     *
     * @param elements
     * @return ResponsiveUIChunkValidator
     */
    public ResponsiveUIChunkValidator findElements(java.util.List<WebElement> elements) {
        return new ResponsiveUIChunkValidator(driver, elements);
    }

    /**
     * Change units to Pixels or % (Units.PX, Units.PERCENT)
     *
     * @param units
     * @return UIValidator
     */
    public ResponsiveUIValidator changeMetricsUnitsTo(Units units) {
        this.units = units;
        return this;
    }

    /**
     * Methods needs to be called to collect all the results in JSON file and screenshots
     *
     * @return ResponsiveUIValidator
     */
    public ResponsiveUIValidator drawMap() {
        withReport = true;
        return this;
    }

    /**
     * Call method to summarize and validate the results (can be called with drawMap(). In this case result will be only True or False)
     *
     * @return boolean
     */
    public boolean validate() {
        JSONObject jsonResults = new JSONObject();
        jsonResults.put(ERROR_KEY, false);

        if (rootElement != null) {
            if (!errorMessage.isEmpty()) {
                jsonResults.put(ERROR_KEY, true);
                jsonResults.put(DETAILS, errorMessage);

                if (withReport) {
                    try {
                        screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
                        img = ImageIO.read(screenshot);
                    } catch (Exception e) {
                        LOG.error("Failed to create screenshot file: " + e.getMessage());
                    }

                    JSONObject rootDetails = new JSONObject();
                    rootDetails.put(X, getX(rootElement));
                    rootDetails.put(Y, getY(rootElement));
                    rootDetails.put(WIDTH, getWidth(rootElement));
                    rootDetails.put(HEIGHT, getHeight(rootElement));

                    jsonResults.put(SCENARIO, scenarioName);
                    jsonResults.put(ROOT_ELEMENT, rootDetails);
                    jsonResults.put(TIME_EXECUTION, String.valueOf(System.currentTimeMillis() - startTime) + " milliseconds");
                    jsonResults.put(ELEMENT_NAME, rootElementReadableName);
                    jsonResults.put(SCREENSHOT, rootElementReadableName.replace(" ", "") + "-" + screenshot.getName());

                    long ms = System.currentTimeMillis();
                    String uuid = Helper.getGeneratedStringWithLength(7);
                    String jsonFileName = rootElementReadableName.replace(" ", "") + "-automotion" + ms + uuid + ".json";
                    try (Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(TARGET_AUTOMOTION_JSON + jsonFileName), StandardCharsets.UTF_8))) {
                        writer.write(jsonResults.toJSONString());
                    } catch (IOException ex) {
                        LOG.error("Cannot create json report: " + ex.getMessage());
                    }
                    jsonFiles.add(jsonFileName);
                    try {
                        File file = new File(TARGET_AUTOMOTION_JSON + rootElementReadableName.replace(" ", "") + "-automotion" + ms + uuid + ".json");
                        if (file.getParentFile().mkdirs()) {
                            if (file.createNewFile()) {
                                BufferedWriter writer = new BufferedWriter(new FileWriter(file));
                                writer.write(jsonResults.toJSONString());
                                writer.close();
                            }
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    if ((boolean) jsonResults.get(ERROR_KEY)) {
                        drawScreenshot();
                    }
                }
            }
        } else {
            jsonResults.put(ERROR_KEY, true);
            jsonResults.put(DETAILS, "Set root web element");
        }

        return !((boolean) jsonResults.get(ERROR_KEY));
    }

    /**
     * Call method to generate HTML report
     */
    public void generateReport() {
        if (withReport && !jsonFiles.isEmpty()) {
            try {
                new HtmlReportBuilder().buildReport(jsonFiles);
            } catch (IOException | ParseException | InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Call method to generate HTML report with specified file report name
     *
     * @param name
     */
    public void generateReport(String name) {
        if (withReport && !jsonFiles.isEmpty()) {
            try {
                new HtmlReportBuilder().buildReport(name, jsonFiles);
            } catch (IOException | ParseException | InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    void drawScreenshot() {
        if (img != null) {
            g = img.createGraphics();

            drawRoot(rootColor);

            for (Object obj : errorMessage) {
                JSONObject det = (JSONObject) obj;
                JSONObject details = (JSONObject) det.get(REASON);
                JSONObject numE = (JSONObject) details.get(ELEMENT);

                if (numE != null) {
                    float x = (float) numE.get(X);
                    float y = (float) numE.get(Y);
                    float width = (float) numE.get(WIDTH);
                    float height = (float) numE.get(HEIGHT);

                    g.setColor(highlightedElementsColor);
                    g.setStroke(new BasicStroke(2));
                    g.drawRect(retinaValue((int) x), retinaValue(mobileY((int) y)), retinaValue((int) width), retinaValue((int) height));
                }
            }

            try {
                ImageIO.write(img, "png", screenshot);
                File file = new File(TARGET_AUTOMOTION_IMG + rootElementReadableName.replace(" ", "") + "-" + screenshot.getName());
                FileUtils.copyFile(screenshot, file);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            LOG.error("Taking of screenshot was failed for some reason.");
        }
    }

    void validateElementsAreNotOverlapped(List<WebElement> rootElements) {
        for (WebElement el1 : rootElements) {
            for (WebElement el2 : rootElements) {
                if (!el1.equals(el2)) {
                    if (elementsAreOverlapped(el1, el2)) {
                        putJsonDetailsWithElement("Elements are overlapped", el1);
                        break;
                    }
                }
            }
        }
    }

    void validateGridAlignment(int columns, int rows) {
        if (rootElements != null) {
            ConcurrentSkipListMap<Integer, AtomicLong> map = new ConcurrentSkipListMap<>();
            for (WebElement el : rootElements) {
                Integer y = getY(el);

                map.putIfAbsent(y, new AtomicLong(0));
                map.get(y).incrementAndGet();
            }

            int mapSize = map.size();
            if (rows > 0) {
                if (mapSize != rows) {
                    putJsonDetailsWithoutElement(String.format("Elements in a grid are not aligned properly. Looks like grid has wrong amount of rows. Expected is %d. Actual is %d", rows, mapSize));
                }
            }

            if (columns > 0) {
                int errorLastLine = 0;
                int rowCount = 1;
                for (Map.Entry<Integer, AtomicLong> entry : map.entrySet()) {
                    if (rowCount <= mapSize) {
                        int actualInARow = entry.getValue().intValue();
                        if (actualInARow != columns) {
                            errorLastLine++;
                            if (errorLastLine > 1) {
                                putJsonDetailsWithoutElement(String.format("Elements in a grid are not aligned properly in row #%d. Expected %d elements in a row. Actually it's %d", rowCount, columns, actualInARow));
                            }
                        }
                        rowCount++;
                    }
                }
            }
        }
    }

    void validateRightOffsetForChunk(List<WebElement> elements) {
        for (int i = 0; i < elements.size() - 1; i++) {
            if (!elementsHaveEqualRightOffset(elements.get(i), elements.get(i + 1))) {
                putJsonDetailsWithElement(String.format("Element #%d has not the same right offset as element #%d", i + 1, i + 2), elements.get(i + 1));
            }
        }
    }

    void validateLeftOffsetForChunk(List<WebElement> elements) {
        for (int i = 0; i < elements.size() - 1; i++) {
            if (!elementsHaveEqualLeftOffset(elements.get(i), elements.get(i + 1))) {
                putJsonDetailsWithElement(String.format("Element #%d has not the same left offset as element #%d", i + 1, i + 2), elements.get(i + 1));
            }
        }
    }

    void validateTopOffsetForChunk(List<WebElement> elements) {
        for (int i = 0; i < elements.size() - 1; i++) {
            if (!elementsHaveEqualTopOffset(elements.get(i), elements.get(i + 1))) {
                putJsonDetailsWithElement(String.format("Element #%d has not the same top offset as element #%d", i + 1, i + 2), elements.get(i + 1));
            }
        }
    }

    void validateBottomOffsetForChunk(List<WebElement> elements) {
        for (int i = 0; i < elements.size() - 1; i++) {
            if (!elementsHaveEqualBottomOffset(elements.get(i), elements.get(i + 1))) {
                putJsonDetailsWithElement(String.format("Element #%d has not the same bottom offset as element #%d", i + 1, i + 2), elements.get(i + 1));
            }
        }
    }

    void validateRightOffsetForElements(WebElement element, String readableName) {
        if (!element.equals(rootElement)) {
            if (!elementsHaveEqualRightOffset(rootElement, element)) {
                putJsonDetailsWithElement(String.format("Element '%s' has not the same right offset as element '%s'", rootElementReadableName, readableName), element);
            }
        }
    }

    void validateLeftOffsetForElements(WebElement element, String readableName) {
        if (!element.equals(rootElement)) {
            if (!elementsHaveEqualLeftOffset(rootElement, element)) {
                putJsonDetailsWithElement(String.format("Element '%s' has not the same left offset as element '%s'", rootElementReadableName, readableName), element);
            }
        }
    }

    void validateTopOffsetForElements(WebElement element, String readableName) {
        if (!element.equals(rootElement)) {
            if (!elementsHaveEqualTopOffset(rootElement, element)) {
                putJsonDetailsWithElement(String.format("Element '%s' has not the same top offset as element '%s'", rootElementReadableName, readableName), element);
            }
        }
    }

    void validateBottomOffsetForElements(WebElement element, String readableName) {
        if (!element.equals(rootElement)) {
            if (!elementsHaveEqualBottomOffset(rootElement, element)) {
                putJsonDetailsWithElement(String.format("Element '%s' has not the same bottom offset as element '%s'", rootElementReadableName, readableName), element);
            }
        }
    }

    void validateNotOverlappingWithElements(WebElement element, String readableName) {
        if (!element.equals(rootElement)) {
            if (elementsAreOverlapped(rootElement, element)) {
                putJsonDetailsWithElement(String.format("Element '%s' is overlapped with element '%s' but should not", rootElementReadableName, readableName), element);
            }
        }
    }

    void validateOverlappingWithElements(WebElement element, String readableName) {
        if (!element.equals(rootElement)) {
            if (!elementsAreOverlapped(rootElement, element)) {
                putJsonDetailsWithElement(String.format("Element '%s' is not overlapped with element '%s' but should be", rootElementReadableName, readableName), element);
            }
        }
    }

    void validateMaxOffset(int top, int right, int bottom, int left) {
        int rootElementRightOffset = getRightOffset(rootElement);
        int rootElementBottomOffset = getBottomOffset(rootElement);
        if (getX(rootElement) > left) {
            putJsonDetailsWithoutElement(String.format("Expected max left offset of element  '%s' is: %spx. Actual left offset is: %spx", rootElementReadableName, left, getX(rootElement)));
        }
        if (getY(rootElement) > top) {
            putJsonDetailsWithoutElement(String.format("Expected max top offset of element '%s' is: %spx. Actual top offset is: %spx", rootElementReadableName, top, getY(rootElement)));
        }
        if (rootElementRightOffset > right) {
            putJsonDetailsWithoutElement(String.format("Expected max right offset of element  '%s' is: %spx. Actual right offset is: %spx", rootElementReadableName, right, rootElementRightOffset));
        }
        if (rootElementBottomOffset > bottom) {
            putJsonDetailsWithoutElement(String.format("Expected max bottom offset of element  '%s' is: %spx. Actual bottom offset is: %spx", rootElementReadableName, bottom, rootElementBottomOffset));
        }
    }

    void validateMinOffset(int top, int right, int bottom, int left) {
        int rootElementRightOffset = getRightOffset(rootElement);
        int rootElementBottomOffset = getBottomOffset(rootElement);
        if (getX(rootElement) < left) {
            putJsonDetailsWithoutElement(String.format("Expected min left offset of element  '%s' is: %spx. Actual left offset is: %spx", rootElementReadableName, left, getX(rootElement)));
        }
        if (getY(rootElement) < top) {
            putJsonDetailsWithoutElement(String.format("Expected min top offset of element  '%s' is: %spx. Actual top offset is: %spx", rootElementReadableName, top, getY(rootElement)));
        }
        if (rootElementRightOffset < right) {
            putJsonDetailsWithoutElement(String.format("Expected min top offset of element  '%s' is: %spx. Actual right offset is: %spx", rootElementReadableName, right, rootElementRightOffset));
        }
        if (rootElementBottomOffset < bottom) {
            putJsonDetailsWithoutElement(String.format("Expected min bottom offset of element  '%s' is: %spx. Actual bottom offset is: %spx", rootElementReadableName, bottom, rootElementBottomOffset));
        }
    }

    void validateMaxHeight(int height) {
        if (getHeight(rootElement) > height) {
            putJsonDetailsWithoutElement(String.format("Expected max height of element  '%s' is: %spx. Actual height is: %spx", rootElementReadableName, height, getHeight(rootElement)));
        }
    }

    void validateMinHeight(int height) {
        if (getHeight(rootElement) < height) {
            putJsonDetailsWithoutElement(String.format("Expected min height of element '%s' is: %spx. Actual height is: %spx", rootElementReadableName, height, getHeight(rootElement)));
        }
    }

    void validateSameHeight(WebElement element, String readableName) {
        if (!element.equals(rootElement)) {
            int h = getHeight(element);
            if (h != getHeight(rootElement)) {
                putJsonDetailsWithElement(String.format("Element '%s' has not the same height as %s. Height of '%s' is %spx. Height of element is %spx", rootElementReadableName, readableName, rootElementReadableName, getHeight(rootElement), h), element);
            }
        }
    }

    void validateMaxWidth(int width) {
        if (getWidth(rootElement) > width) {
            putJsonDetailsWithoutElement(String.format("Expected max width of element '%s' is: %spx. Actual width is: %spx", rootElementReadableName, width, getWidth(rootElement)));
        }
    }

    void validateMinWidth(int width) {
        if (getWidth(rootElement) < width) {
            putJsonDetailsWithoutElement(String.format("Expected min width of element '%s' is: %spx. Actual width is: %spx", rootElementReadableName, width, getWidth(rootElement)));
        }
    }

    void validateSameWidth(WebElement element, String readableName) {
        if (!element.equals(rootElement)) {
            int w = getWidth(element);
            if (w != getWidth(rootElement)) {
                putJsonDetailsWithElement(String.format("Element '%s' has not the same width as %s. Width of '%s' is %spx. Width of element is %spx", rootElementReadableName, readableName, rootElementReadableName, getWidth(rootElement), w), element);
            }
        }
    }

    void validateSameSize(WebElement element, String readableName) {
        if (!element.equals(rootElement)) {
            int h = getHeight(element);
            int w = getWidth(element);
            if (h != getHeight(rootElement) || w != getWidth(rootElement)) {
                putJsonDetailsWithElement(String.format("Element '%s' has not the same size as %s. Size of '%s' is %spx x %spx. Size of element is %spx x %spx", rootElementReadableName, readableName, rootElementReadableName, getWidth(rootElement), getHeight(rootElement), w, h), element);
            }
        }
    }

    void validateSameSize(List<WebElement> elements, int type) {
        for (int i = 0; i < elements.size() - 1; i++) {
            int h1 = getHeight(elements.get(i));
            int w1 = getWidth(elements.get(i));
            int h2 = getHeight(elements.get(i + 1));
            int w2 = getWidth(elements.get(i + 1));
            switch (type) {
                case 0:
                    if (h1 != h2 || w1 != w2) {
                        putJsonDetailsWithElement(String.format("Element #%d has different size. Element size is: [%d, %d]", (i + 1), getWidth(elements.get(i)), getHeight(elements.get(i))), elements.get(i));
                        putJsonDetailsWithElement(String.format("Element #%d has different size. Element size is: [%d, %d]", (i + 2), getWidth(elements.get(i + 1)), getHeight(elements.get(i + 1))), elements.get(i + 1));
                    }
                    break;
                case 1:
                    if (w1 != w2) {
                        putJsonDetailsWithElement(String.format("Element #%d has different width. Element width is: [%d, %d]", (i + 1), getWidth(elements.get(i)), getHeight(elements.get(i))), elements.get(i));
                        putJsonDetailsWithElement(String.format("Element #%d has different width. Element width is: [%d, %d]", (i + 2), getWidth(elements.get(i + 1)), getHeight(elements.get(i + 1))), elements.get(i + 1));
                    }
                    break;
                case 2:
                    if (h1 != h2) {
                        putJsonDetailsWithElement(String.format("Element #%d has different height. Element height is: [%d, %d]", (i + 1), getWidth(elements.get(i)), getHeight(elements.get(i))), elements.get(i));
                        putJsonDetailsWithElement(String.format("Element #%d has different height. Element height is: [%d, %d]", (i + 2), getWidth(elements.get(i + 1)), getHeight(elements.get(i + 1))), elements.get(i + 1));
                    }
            }
        }
    }

    void validateNotSameSize(WebElement element, String readableName) {
        if (!element.equals(rootElement)) {
            int h = getHeight(element);
            int w = getWidth(element);
            if (h == getHeight(rootElement) && w == getWidth(rootElement)) {
                putJsonDetailsWithElement(String.format("Element '%s' has the same size as %s. Size of '%s' is %spx x %spx. Size of element is %spx x %spx", rootElementReadableName, readableName, rootElementReadableName, getWidth(rootElement), getHeight(rootElement), w, h), element);
            }
        }
    }

    void validateNotSameSize(List<WebElement> elements, int type) {
        for (int i = 0; i < elements.size() - 1; i++) {
            int h1 = getHeight(elements.get(i));
            int w1 = getWidth(elements.get(i));
            int h2 = getHeight(elements.get(i + 1));
            int w2 = getWidth(elements.get(i + 1));
            switch (type) {
                case 0:
                    if (h1 == h2 && w1 == w2) {
                        putJsonDetailsWithElement(String.format("Element #%d has same size. Element size is: [%d, %d]", (i + 1), getWidth(elements.get(i)), getHeight(elements.get(i))), elements.get(i));
                        putJsonDetailsWithElement(String.format("Element #%d has same size. Element size is: [%d, %d]", (i + 2), getWidth(elements.get(i + 1)), getHeight(elements.get(i + 1))), elements.get(i + 1));
                    }
                    break;
                case 1:
                    if (w1 == w2) {
                        putJsonDetailsWithElement(String.format("Element #%d has same width. Element width is: [%d, %d]", (i + 1), getWidth(elements.get(i)), getHeight(elements.get(i))), elements.get(i));
                        putJsonDetailsWithElement(String.format("Element #%d has same width. Element width is: [%d, %d]", (i + 2), getWidth(elements.get(i + 1)), getHeight(elements.get(i + 1))), elements.get(i + 1));
                    }
                    break;
                case 2:
                    if (h1 == h2) {
                        putJsonDetailsWithElement(String.format("Element #%d has same height. Element height is: [%d, %d]", (i + 1), getWidth(elements.get(i)), getHeight(elements.get(i))), elements.get(i));
                        putJsonDetailsWithElement(String.format("Element #%d has same height. Element height is: [%d, %d]", (i + 2), getWidth(elements.get(i + 1)), getHeight(elements.get(i + 1))), elements.get(i + 1));
                    }
            }
        }
    }

    void validateBelowElement(WebElement element, int minMargin, int maxMargin) {
        int yBelowElement = getY(element);
        int marginBetweenRoot = yBelowElement - (getY(rootElement) + getHeight(rootElement));
        if (marginBetweenRoot < minMargin || marginBetweenRoot > maxMargin) {
            putJsonDetailsWithElement(String.format("Below element aligned not properly. Expected margin should be between %spx and %spx. Actual margin is %spx", minMargin, maxMargin, marginBetweenRoot), element);
        }
    }

    void validateBelowElement(WebElement element) {
        List<WebElement> elements = new ArrayList<>();
        elements.add(rootElement);
        elements.add(element);

        if (!PageValidator.elementsAreAlignedVertically(elements)) {
            putJsonDetailsWithoutElement("Below element aligned not properly");
        }
    }

    void validateAboveElement(WebElement element, int minMargin, int maxMargin) {
        int yAboveElement = getY(element);
        int heightAboveElement = getHeight(element);
        int marginBetweenRoot = getY(rootElement) - (yAboveElement + heightAboveElement);
        if (marginBetweenRoot < minMargin || marginBetweenRoot > maxMargin) {
            putJsonDetailsWithElement(String.format("Above element aligned not properly. Expected margin should be between %spx and %spx. Actual margin is %spx", minMargin, maxMargin, marginBetweenRoot), element);
        }
    }

    void validateAboveElement(WebElement element) {
        List<WebElement> elements = new ArrayList<>();
        elements.add(element);
        elements.add(rootElement);

        if (!PageValidator.elementsAreAlignedVertically(elements)) {
            putJsonDetailsWithoutElement("Above element aligned not properly");
        }
    }

    void validateRightElement(WebElement element, int minMargin, int maxMargin) {
        int xRightElement = getX(element);
        int marginBetweenRoot = xRightElement - (getX(rootElement) + getWidth(rootElement));
        if (marginBetweenRoot < minMargin || marginBetweenRoot > maxMargin) {
            putJsonDetailsWithElement(String.format("Right element aligned not properly. Expected margin should be between %spx and %spx. Actual margin is %spx", minMargin, maxMargin, marginBetweenRoot), element);
        }
    }

    void validateRightElement(WebElement element) {
        List<WebElement> elements = new ArrayList<>();
        elements.add(rootElement);
        elements.add(element);

        if (!PageValidator.elementsAreAlignedHorizontally(elements)) {
            putJsonDetailsWithoutElement("Right element aligned not properly");
        }
    }

    void validateLeftElement(WebElement leftElement, int minMargin, int maxMargin) {
        int xLeftElement = getX(leftElement);
        int widthLeftElement = getWidth(leftElement);
        int marginBetweenRoot = getX(rootElement) - (xLeftElement + widthLeftElement);
        if (marginBetweenRoot < minMargin || marginBetweenRoot > maxMargin) {
            putJsonDetailsWithElement(String.format("Left element aligned not properly. Expected margin should be between %spx and %spx. Actual margin is %spx", minMargin, maxMargin, marginBetweenRoot), leftElement);
        }
    }

    void validateLeftElement(WebElement leftElement) {
        List<WebElement> elements = new ArrayList<>();
        elements.add(leftElement);
        elements.add(rootElement);

        if (!PageValidator.elementsAreAlignedHorizontally(elements)) {
            putJsonDetailsWithoutElement("Left element aligned not properly");
        }
    }

    void validateEqualLeftRightOffset(WebElement element, String rootElementReadableName) {
        if (!elementHasEqualLeftRightOffset(element)) {
            putJsonDetailsWithElement(String.format("Element '%s' has not equal left and right offset. Left offset is %dpx, right is %dpx", rootElementReadableName, getX(element), getRightOffset(element)), element);
        }
    }

    void validateEqualTopBottomOffset(WebElement element, String rootElementReadableName) {
        if (!elementHasEqualTopBottomOffset(element)) {
            putJsonDetailsWithElement(String.format("Element '%s' has not equal top and bottom offset. Top offset is %dpx, bottom is %dpx", rootElementReadableName, getY(element), getBottomOffset(element)), element);
        }
    }

    void validateEqualLeftRightOffset(List<WebElement> elements) {
        for (WebElement element : elements) {
            if (!elementHasEqualLeftRightOffset(element)) {
                putJsonDetailsWithElement(String.format("Element '%s' has not equal left and right offset. Left offset is %dpx, right is %dpx", getFormattedMessage(element), getX(element), getRightOffset(element)), element);
            }
        }
    }

    void validateEqualTopBottomOffset(List<WebElement> elements) {
        for (WebElement element : elements) {
            if (!elementHasEqualTopBottomOffset(element)) {
                putJsonDetailsWithElement(String.format("Element '%s' has not equal top and bottom offset. Top offset is %dpx, bottom is %dpx", getFormattedMessage(element), getY(element), getBottomOffset(element)), element);
            }
        }
    }

    void drawRoot(Color color) {
        g.setColor(color);
        g.setStroke(new BasicStroke(2));
        g.drawRect(retinaValue(getX(rootElement)), retinaValue(mobileY(getY(rootElement))), retinaValue(getWidth(rootElement)), retinaValue(getHeight(rootElement)));
        //g.fillRect(retinaValue(xRoot), retinaValue((yRoot), retinaValue(widthRoot), retinaValue(heightRoot));

        Stroke dashed = new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{9}, 0);
        g.setStroke(dashed);
        g.setColor(linesColor);
        if (drawLeftOffsetLine) {
            g.drawLine(retinaValue(getX(rootElement)), 0, retinaValue(getX(rootElement)), retinaValue(img.getHeight()));
        }
        if (drawRightOffsetLine) {
            g.drawLine(retinaValue(getX(rootElement) + getWidth(rootElement)), 0, retinaValue(getX(rootElement) + getWidth(rootElement)), retinaValue(img.getHeight()));
        }
        if (drawTopOffsetLine) {
            g.drawLine(0, retinaValue(mobileY(getY(rootElement))), retinaValue(img.getWidth()), retinaValue(getY(rootElement)));
        }
        if (drawBottomOffsetLine) {
            g.drawLine(0, retinaValue(mobileY(getY(rootElement) + getHeight(rootElement))), retinaValue(img.getWidth()), retinaValue(getY(rootElement) + getHeight(rootElement)));
        }
    }

    void putJsonDetailsWithoutElement(String message) {
        JSONObject details = new JSONObject();
        JSONObject mes = new JSONObject();
        mes.put(MESSAGE, message);
        details.put(REASON, mes);
        errorMessage.add(details);
    }

    private void putJsonDetailsWithElement(String message, WebElement element) {
        float xContainer = getX(element);
        float yContainer = getY(element);
        float widthContainer = getWidth(element);
        float heightContainer = getHeight(element);

        JSONObject details = new JSONObject();
        JSONObject elDetails = new JSONObject();
        elDetails.put(X, xContainer);
        elDetails.put(Y, yContainer);
        elDetails.put(WIDTH, widthContainer);
        elDetails.put(HEIGHT, heightContainer);
        JSONObject mes = new JSONObject();
        mes.put(MESSAGE, message);
        mes.put(ELEMENT, elDetails);
        details.put(REASON, mes);
        errorMessage.add(details);
    }

    int getConvertedInt(int i, boolean horizontal) {
        if (units.equals(PX)) {
            return i;
        } else {
            if (horizontal) {
                return (i * pageWidth) / 100;
            } else {
                return (i * pageHeight) / 100;
            }
        }
    }

    String getFormattedMessage(WebElement element) {
        return String.format("with properties: tag=[%s], id=[%s], class=[%s], text=[%s], coord=[%s,%s], size=[%s,%s]",
                element.getTagName(),
                element.getAttribute("id"),
                element.getAttribute("class"),
                element.getText().length() < 10 ? element.getText() : element.getText().substring(0, 10) + "...",
                String.valueOf(getX(element)),
                String.valueOf(getY(element)),
                String.valueOf(getWidth(element)),
                String.valueOf(getHeight(element)));
    }

    int retinaValue(int value) {
        if (!isMobile()) {
            int zoom = Integer.parseInt(currentZoom.replace("%", ""));
            if (zoom > 100) {
                value = (int) (value + (value * Math.abs(zoom - 100f) / 100f));
            } else if (zoom < 100) {
                value = (int) (value - (value * Math.abs(zoom - 100f) / 100f));
            }
            if (isRetinaDisplay() && isChrome()) {
                return 2 * value;
            } else {
                return value;
            }
        } else {
            if (isIOS()) {
                String[] iOS_RETINA_DEVICES = {
                        "iPhone 4", "iPhone 4s",
                        "iPhone 5", "iPhone 5s",
                        "iPhone 6", "iPhone 6s",
                        "iPad Mini 2",
                        "iPad Mini 4",
                        "iPad Air 2",
                        "iPad Pro"
                };
                if (Arrays.asList(iOS_RETINA_DEVICES).contains(getDevice())) {
                    return 2 * value;
                } else {
                    return value;
                }
            } else {
                return value;
            }
        }
    }

    int mobileY(int value) {
        if (isMobile() && ((AppiumDriver) driver).getContext().startsWith("WEB")) {
            if (isIOS()) {
                if (isMobileTopBar) {
                    return value + 20;
                } else {
                    return value;
                }
            } else if (isAndroid()) {
                if (isMobileTopBar) {
                    return value + 20;
                } else {
                    return value;
                }
            } else {
                return value;
            }
        } else {
            return value;
        }
    }

    long getPageWidth() {
        JavascriptExecutor executor = (JavascriptExecutor) driver;
        if (!isMobile()) {
            if (isFirefox()) {
                currentZoom = (String) executor.executeScript("document.body.style.MozTransform");
            } else {
                currentZoom = (String) executor.executeScript("return document.body.style.zoom;");
            }
            if (currentZoom == null || currentZoom.equals("100%") || currentZoom.equals("")) {
                currentZoom = "100%";
                return (long) executor.executeScript("if (self.innerWidth) {return self.innerWidth;} if (document.documentElement && document.documentElement.clientWidth) {return document.documentElement.clientWidth;}if (document.body) {return document.body.clientWidth;}");
            } else {
                return (long) executor.executeScript("return document.getElementsByTagName('body')[0].offsetWidth");
            }
        } else {
            if (isNativeMobileContext() || isIOS()) {
                return driver.manage().window().getSize().getWidth();
            } else {
                return (long) executor.executeScript("if (self.innerWidth) {return self.innerWidth;} if (document.documentElement && document.documentElement.clientWidth) {return document.documentElement.clientWidth;}if (document.body) {return document.body.clientWidth;}");
            }
        }
    }

    long getPageHeight() {
        JavascriptExecutor executor = (JavascriptExecutor) driver;
        if (!isMobile()) {
            if (isFirefox()) {
                currentZoom = (String) executor.executeScript("document.body.style.MozTransform");
            } else {
                currentZoom = (String) executor.executeScript("return document.body.style.zoom;");
            }
            if (currentZoom == null || currentZoom.equals("100%") || currentZoom.equals("")) {
                currentZoom = "100%";
                return (long) executor.executeScript("if (self.innerHeight) {return self.innerHeight;} if (document.documentElement && document.documentElement.clientHeight) {return document.documentElement.clientHeight;}if (document.body) {return document.body.clientHeight;}");
            } else {
                return (long) executor.executeScript("return document.getElementsByTagName('body')[0].offsetHeight");
            }
        } else {
            if (isNativeMobileContext() || isIOS()) {
                return driver.manage().window().getSize().getHeight();
            } else {
                return (long) executor.executeScript("if (self.innerHeight) {return self.innerHeight;} if (document.documentElement && document.documentElement.clientHeight) {return document.documentElement.clientHeight;}if (document.body) {return document.body.clientHeight;}");
            }
        }
    }

    private boolean isNativeMobileContext() {
        return ((AppiumDriver) driver).getContext().contains("NATIVE");
    }

    void validateInsideOfContainer(WebElement containerElement, String readableContainerName) {
        Rectangle2D.Double elementRectangle = rectangle(containerElement);
        if (rootElements == null || rootElements.isEmpty()) {
            Rectangle2D.Double rootRectangle = rectangle(rootElement);
            if (!elementRectangle.contains(rootRectangle)) {
                putJsonDetailsWithElement(String.format("Element '%s' is not inside of '%s'", rootElementReadableName, readableContainerName), containerElement);
            }
        } else {
            for (WebElement el : rootElements) {
                if (!elementRectangle.contains(rectangle(el))) {
                    putJsonDetailsWithElement(String.format("Element is not inside of '%s'", readableContainerName), containerElement);
                }
            }
        }
    }

    void validateInsideOfContainer(WebElement element, String readableContainerName, Padding padding) {
        int top = getConvertedInt(padding.getTop(), false);
        int right = getConvertedInt(padding.getRight(), true);
        int bottom = getConvertedInt(padding.getBottom(), false);
        int left = getConvertedInt(padding.getLeft(), true);

        Rectangle2D.Double paddedRootRectangle = new Rectangle2D.Double(
                getX(rootElement) - left,
                getY(rootElement) - top,
                getWidth(rootElement) + left + right,
                getHeight(rootElement) + top + bottom);

        int paddingTop = getY(rootElement) - getY(element);
        int paddingLeft = getX(rootElement) - getX(element);
        int paddingBottom = getCornerY(element) - getCornerY(rootElement);
        int paddingRight = getCornerX(element) - getCornerX(rootElement);

        if (!rectangle(element).contains(paddedRootRectangle)) {
            putJsonDetailsWithElement(String.format("Padding of element '%s' is incorrect. Expected padding: top[%d], right[%d], bottom[%d], left[%d]. Actual padding: top[%d], right[%d], bottom[%d], left[%d]",
                    rootElementReadableName, top, right, bottom, left, paddingTop, paddingRight, paddingBottom, paddingLeft), element);
        }
    }

    private Rectangle2D.Double rectangle(WebElement element) {
        return new Rectangle2D.Double(
                getX(element),
                getY(element),
                getWidth(element),
                getHeight(element));
    }

    private int getRightOffset(WebElement element) {
        return pageWidth - getCornerX(element);
    }

    private int getBottomOffset(WebElement element) {
        return pageHeight - getCornerY(element);
    }

    private boolean elementsAreOverlapped(WebElement rootElement, WebElement elementOverlapWith) {
        return rectangle(rootElement).intersects(rectangle(elementOverlapWith));
    }

    private boolean elementsHaveEqualLeftOffset(WebElement element, WebElement elementToCompare) {
        return getX(element) == getX(elementToCompare);
    }

    private boolean elementsHaveEqualRightOffset(WebElement element, WebElement elementToCompare) {
        return getCornerX(element) == getCornerX(elementToCompare);
    }

    private boolean elementsHaveEqualTopOffset(WebElement element, WebElement elementToCompare) {
        return getY(element) == getY(elementToCompare);
    }

    private boolean elementsHaveEqualBottomOffset(WebElement element, WebElement elementToCompare) {
        return getCornerY(element) == getCornerY(elementToCompare);
    }

    private boolean elementHasEqualLeftRightOffset(WebElement element) {
        return getX(element) == getRightOffset(element);
    }

    private boolean elementHasEqualTopBottomOffset(WebElement element) {
        return getY(element) == getBottomOffset(element);
    }

    private int getX(WebElement element) {
        return element.getLocation().getX();
    }

    private int getY(WebElement element) {
        return element.getLocation().getY();
    }

    private int getWidth(WebElement element) {
        return element.getSize().getWidth();
    }

    private int getHeight(WebElement element) {
        return element.getSize().getHeight();
    }

    private int getCornerX(WebElement element) {
        return getX(element) + getWidth(element);
    }

    private int getCornerY(WebElement element) {
        return getY(element) + getHeight(element);
    }

    public enum Units {
        PX,
        PERCENT
    }

}
