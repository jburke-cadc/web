/*
 * ***********************************************************************
 * ******************  CANADIAN ASTRONOMY DATA CENTRE  *******************
 * *************  CENTRE CANADIEN DE DONNÉES ASTRONOMIQUES  **************
 * <p/>
 * (c) 2010.                            (c) 2010.
 * Government of Canada                 Gouvernement du Canada
 * National Research Council            Conseil national de recherches
 * Ottawa, Canada, K1A 0R6              Ottawa, Canada, K1A 0R6
 * All rights reserved                  Tous droits réservés
 * <p/>
 * NRC disclaims any warranties,        Le CNRC dénie toute garantie
 * expressed, implied, or               énoncée, implicite ou légale,
 * statutory, of any kind with          de quelque nature que ce
 * respect to the software,             soit, concernant le logiciel,
 * including without limitation         y compris sans restriction
 * any warranty of merchantability      toute garantie de valeur
 * or fitness for a particular          marchande ou de pertinence
 * purpose. NRC shall not be            pour un usage particulier.
 * liable in any event for any          Le CNRC ne pourra en aucun cas
 * damages, whether direct or           être tenu responsable de tout
 * indirect, special or general,        dommage, direct ou indirect,
 * consequential or incidental,         particulier ou général,
 * arising from the use of the          accessoire ou fortuit, résultant
 * software.  Neither the name          de l'utilisation du logiciel. Ni
 * of the National Research             le nom du Conseil National de
 * Council of Canada nor the            Recherches du Canada ni les noms
 * names of its contributors may        de ses  participants ne peuvent
 * be used to endorse or promote        être utilisés pour approuver ou
 * products derived from this           promouvoir les produits dérivés
 * software without specific prior      de ce logiciel sans autorisation
 * written permission.                  préalable et particulière
 * par écrit.
 * <p/>
 * This file is part of the             Ce fichier fait partie du projet
 * OpenCADC project.                    OpenCADC.
 * <p/>
 * OpenCADC is free software:           OpenCADC est un logiciel libre ;
 * you can redistribute it and/or       vous pouvez le redistribuer ou le
 * modify it under the terms of         modifier suivant les termes de
 * the GNU Affero General Public        la “GNU Affero General Public
 * License as published by the          License” telle que publiée
 * Free Software Foundation,            par la Free Software Foundation
 * either version 3 of the              : soit la version 3 de cette
 * License, or (at your option)         licence, soit (à votre gré)
 * any later version.                   toute version ultérieure.
 * <p/>
 * OpenCADC is distributed in the       OpenCADC est distribué
 * hope that it will be useful,         dans l’espoir qu’il vous
 * but WITHOUT ANY WARRANTY;            sera utile, mais SANS AUCUNE
 * without even the implied             GARANTIE : sans même la garantie
 * warranty of MERCHANTABILITY          implicite de COMMERCIALISABILITÉ
 * or FITNESS FOR A PARTICULAR          ni d’ADÉQUATION À UN OBJECTIF
 * PURPOSE.  See the GNU Affero         PARTICULIER. Consultez la Licence
 * General Public License for           Générale Publique GNU Affero
 * more details.                        pour plus de détails.
 * <p/>
 * You should have received             Vous devriez avoir reçu une
 * a copy of the GNU Affero             copie de la Licence Générale
 * General Public License along         Publique GNU Affero avec
 * with OpenCADC.  If not, see          OpenCADC ; si ce n’est
 * <http://www.gnu.org/licenses/>.      pas le cas, consultez :
 * <http://www.gnu.org/licenses/>.
 * <p/>
 * ***********************************************************************
 */

package ca.nrc.cadc.web.selenium;


import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import junit.framework.AssertionFailedError;
import org.apache.commons.io.FileUtils;

import org.junit.Rule;
import org.junit.rules.ExternalResource;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.firefox.FirefoxProfile;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.opera.OperaOptions;
import org.openqa.selenium.remote.Augmenter;
import org.openqa.selenium.remote.BrowserType;
import org.openqa.selenium.remote.CapabilityType;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.safari.SafariOptions;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import ca.nrc.cadc.util.StringUtil;


/**
 * Subclasses of this should have the necessary tools to create an automated web application test.
 * <p>
 * TODO: Clean this up to only have shared code.
 */
public abstract class AbstractWebApplicationIntegrationTest {
    // One minute is just too long.
    private static final int TIMEOUT_IN_SECONDS = 60;
    private static final int TIMEOUT_IN_MILLISECONDS = (TIMEOUT_IN_SECONDS * 1000);
    private static final String SELENIUM_SERVER_URL_ENDPOINT = "/wd/hub";
    private static final Map<String, MutableCapabilities> CAPABILITIES_LOOKUP = new HashMap<>();

    static {
        CAPABILITIES_LOOKUP.put("firefox", new FirefoxOptions());
        CAPABILITIES_LOOKUP.put("safari", new SafariOptions());
        CAPABILITIES_LOOKUP.put("chrome", new ChromeOptions());
        CAPABILITIES_LOOKUP.put("opera", new OperaOptions());
    }

    private String seleniumServerURL;
    private String webURL;
    private String endpoint;
    private String username;
    private String password;
    private int currentWaitTime;
    private boolean failOnTimeout;

    protected MutableCapabilities driverCapabilities;
    protected WebDriver driver;


    @Rule
    public ExternalResource testWatcher = new ExternalResource() {
        @Override
        public Statement apply(final Statement base,
                               final Description description) {
            return new Statement() {
                @Override
                public void evaluate() throws Throwable {
                    before();
                    try {
                        base.evaluate();
                    } catch (Throwable t) {
                        captureScreenShot(description.getClassName() + "." + description.getMethodName());
                        throw t;
                    } finally {
                        after();
                    }
                }
            };
        }

        /**
         * Override to set up your specific external resource.
         *
         * @throws Throwable if setup fails (which will disable {@code after}
         */
        @Override
        protected void before() {
            try {
                final String seleniumURL;
                if (seleniumServerURL.contains(SELENIUM_SERVER_URL_ENDPOINT)) {
                    seleniumURL = seleniumServerURL;
                } else {
                    seleniumURL = seleniumServerURL + SELENIUM_SERVER_URL_ENDPOINT;
                }

                System.out.println("Connecting to " + seleniumURL);

                driver = new RemoteWebDriver(new URL(seleniumURL), driverCapabilities);
            } catch (MalformedURLException e) {
                System.err.println("Can't create URL.");
                e.printStackTrace(System.err);
                throw new RuntimeException(e);
            }

            driver.manage().window().maximize();

            final WebDriver.Timeouts timeouts = driver.manage().timeouts();

            // Safari does not support setTimeout.
            if (!driverCapabilities.getBrowserName().contains("afari")) {
                // Set the timeout to four minutes.
                timeouts.pageLoadTimeout(TIMEOUT_IN_MILLISECONDS, TimeUnit.MILLISECONDS);
            }

            timeouts.setScriptTimeout(TIMEOUT_IN_MILLISECONDS, TimeUnit.MILLISECONDS);
        }

        /**
         * Override to tear down your specific external resource.
         */
        @Override
        protected void after() {
            if (driver != null) {
                try {
                    driver.quit();
                } catch (Exception de) {
                    System.err.println("Driver could not quit!");
                    de.printStackTrace(System.err);
                } finally {
                    driver = null;
                }
            }

            System.out.println("Finished.");
        }

        void captureScreenShot(final String methodName) throws IOException {
            final String filename = methodName + ".png";
            final WebDriver augmentedDriver = new Augmenter().augment(driver);
            final File sourceFile = ((TakesScreenshot) augmentedDriver).getScreenshotAs(OutputType.FILE);

            FileUtils.copyFile(sourceFile, new File("./" + filename));

            System.err.println(String.format("Saved screenshot as '%s'", filename));
        }
    };


    public AbstractWebApplicationIntegrationTest() throws Exception {
        // Base Host of the web application to be tested.
        final String seleniumURL = System.getProperty("selenium.server.url");
        if (seleniumURL == null) {
            throw new RuntimeException("selenium.server.url System property not set");
        } else {
            seleniumServerURL = seleniumURL;
        }

        // Schema of the web application to be tested.
        final String driver = System.getProperty("driver");
        if (!StringUtil.hasText(driver) || !CAPABILITIES_LOOKUP.containsKey(driver)) {
            throw new RuntimeException("'driver' System property not set or not supported.");
        } else {
            driverCapabilities = CAPABILITIES_LOOKUP.get(driver.toLowerCase());
            driverCapabilities.setCapability(CapabilityType.HAS_NATIVE_EVENTS, true);
            driverCapabilities.setCapability(CapabilityType.SUPPORTS_JAVASCRIPT, true);

            // Uncomment this when the related bug is fixed.
//            driverCapabilities.setCapability(CapabilityType.UNEXPECTED_ALERT_BEHAVIOUR, UnexpectedAlertBehaviour
//                .IGNORE.toString());
            driverCapabilities.setCapability(CapabilityType.TAKES_SCREENSHOT, true);
        }

        final String userName = System.getProperty("user.name");
        if (!StringUtil.hasText(userName)) {
            System.out.println("No username set!  Set the user.name system "
                                   + "property if BASIC authentication is required.");
        } else {
            setUsername(userName);
        }

        if (StringUtil.hasText(getUsername())) {
            final String userPassword = System.getProperty("user.password");
            if (!StringUtil.hasText(userPassword)) {
                System.out.println("No password set!  Set the user.password system "
                                       + "property if BASIC authentication is required.");
            } else {
                setPassword(userPassword);
            }
        }

        // Base Host of the web application to be tested.
        final String applicationURL = System.getProperty("web.app.url");
        if (!StringUtil.hasText(applicationURL)) {
            throw new RuntimeException("web.app.url System property is missing.");
        } else {
            webURL = applicationURL;
        }

        final String configuredEndpoint = System.getProperty("web.app.endpoint");
        this.endpoint = StringUtil.hasText(configuredEndpoint) ? configuredEndpoint : null;

        System.out.println("Web URL: " + webURL);
        System.out.println("Selenium Server: " + seleniumServerURL);
        System.out.println("Done with Abstract Web Test constructor.");
    }

    /**
     * Navigate to the given location.
     *
     * @param path  The navigation path.
     * @param query The query.
     * @throws Exception For any test problems.
     * @see <a href="https://code.google.com/p/selenium/wiki/PageObjects">Page Objects</a>
     * @deprecated Please use {@link #goTo(String, String, Class)} instead and
     * adapt to the PageObject model.
     */
    public void goTo(final String path, final String query) throws Exception {
        driver.get(webURL + path + (StringUtil.hasText(query) ? ("?" + query) : ""));
    }


    /**
     * Visit the given path with a query attached to it.  Return the page with
     * the given class.
     *
     * @param path      The navigation path.
     * @param query     The query.
     * @param pageClass The class of the returned instance.
     * @param <T>       The type of Page to return.
     * @return A page element.
     * @throws Exception For any test execution errors
     */
    public <T extends AbstractTestWebPage> T goTo(final String path, final String query, final Class<T> pageClass)
        throws Exception {
        final String webAppURL = webURL + path + (StringUtil.hasText(query)
            ? ("?" + query) : "");
        System.out.println("Visiting: " + webAppURL);
        driver.get(webAppURL);

        final Class[] constructorArgTypes = new Class[] {WebDriver.class};
        final Constructor<T> constructor =
            pageClass.getConstructor(constructorArgTypes);
        return constructor.newInstance(driver);
    }

    public void goBack() throws Exception {
        driver.navigate().back();
    }

    /**
     * Like assertTrue, but fails at the end of the test (during tearDown)
     *
     * @param b The boolean flag to check for truthiness.
     */
    public void verifyTrue(final boolean b) throws Exception {
        if (!b) {
            throw new IllegalArgumentException("Verification failed.");
        }
    }

    public void verifyEquals(final Object o1, final Object o2) throws Exception {
        verifyTrue(o1.equals(o2));
    }

    public void check(final By by) throws Exception {
        click(by);
    }

    public void uncheck(final By by) throws Exception {
        if (find(by).isSelected()) {
            click(by);
        }
    }

    public WebElement find(final By by) throws Exception {
        try {
            return driver.findElement(by);
        } catch (Throwable e) {
            System.err.println("No element found: " + by.toString());
            return null;
        }
    }

    public void click(final By by) throws Exception {
        waitForElementPresent(by);
        click(find(by));
    }

    public void click(final WebElement elem) throws Exception {
        elem.click();
    }

    public void resetForm() throws Exception {
        resetForm(By.cssSelector("[type=\"reset\"]"));
    }

    public void resetForm(final By resetButtonBy) throws Exception {
        click(resetButtonBy);
    }

    public void verifyElementChecked(final By by) throws Exception {
        verifyTrue(find(by).isSelected());
    }

    public void verifyElementUnChecked(final By by) throws Exception {
        verifyFalse(find(by).isSelected());
    }

    public boolean elementExists(final By by) throws Exception {
        return (find(by) != null);
    }

    public void verifyElementPresent(final By by) throws Exception {
        final WebElement webElement = find(by);
        verifyFalse(webElement == null);
    }

    public void verifyDisabledInput(final String idSelector) throws Exception {
        final Object obj = executeJavaScript("return document.getElementById('" + idSelector + "').disabled;");
        verifyTrue((obj != null) && ((Boolean) obj));
    }

    public void verifyElementNotPresent(final By by) throws Exception {
        verifyTrue((find(by) == null));
    }

    /**
     * Issue a drag and drop command.
     *
     * @param source      The source element.
     * @param destination The to (target) element to drop into.
     */
    public void dragAndDrop(final By source, final By destination) throws Exception {
        (new Actions(driver)).dragAndDrop(find(source), find(destination)).perform();
    }

    /**
     * Scroll a container (e.g. div) until the element with elementID is
     * visible.
     *
     * @param elementID           The ID of the element to find.
     * @param containerToScrollID The container to scroll.
     * @throws Exception For any test execution errors
     */
    public void scrollVerticallyIntoView(final String elementID,
                                         final String containerToScrollID)
        throws Exception {
        final String script =
            "var myElement = document.getElementById('" + elementID
                + "');"
                + "var topPos = myElement.offsetTop;"
                + "document.getElementById('" + containerToScrollID
                + "').scrollTop = topPos;";

        ((JavascriptExecutor) driver).executeScript(script);
    }


    /**
     * Scroll the Grid.  This is for cadcVOTV grids.
     *
     * @param elementIDToScroll The ID of the container.
     * @throws Exception For any test execution errors
     */
    protected void scrollGrid(final String elementIDToScroll) throws Exception {
        final String findByClassNameLoop =
            "for (i in elems) {"
                + "if((' ' + elems[i].className + ' ').indexOf(' slick-viewport ') > -1) {"
                + "targetDiv = elems[i];break;"
                + "}}";
        final String script =
            "var objDiv = document.getElementById('" + elementIDToScroll
                + "'), targetDiv; var elems = objDiv.getElementsByTagName('div'), i;"
                + findByClassNameLoop
                + " targetDiv.scrollTop += 25;";

        executeJavaScript(script);
    }

    /**
     * Scroll the Grid.  This is for cadcVOTV grids.
     *
     * @param elementIDToScroll The ID of the container.
     * @throws Exception For any test execution errors
     */
    protected void scrollGridHorizontally(final String elementIDToScroll) throws Exception {
        final String findByClassNameLoop =
            "for (i in elems) {"
                + "if((' ' + elems[i].className + ' ').indexOf(' slick-pane-right ') > -1) {"
                + "targetDiv = elems[i];break;"
                + "}}";
        final String script =
            "var objDiv = document.getElementById('" + elementIDToScroll
                + "'), targetDiv; var elems = objDiv.getElementsByTagName('div'), i;"
                + findByClassNameLoop
                + " targetDiv.scrollRight += 125;";

        executeJavaScript(script);
    }

    public void verifyTextPresent(final By by, final String value) throws Exception {
        verifyTrue(getText(by).contains(value));
    }

    public void verifyTextMatches(final By by, final String regex) throws Exception {
        verifyTrue(getText(by).matches(regex));
    }

    public void verifyText(final By by, final String value) throws Exception {
        verifyEquals(value, getText(by));
    }

    public String getText(final By by) throws Exception {
        return find(by).getText();
    }

    public boolean isTextPresent(final String text) throws Exception {
        return driver.getPageSource().contains(text);
    }

    public void verifyTextPresent(final String text) throws Exception {
        verifyTrue(isTextPresent(text));
    }

    public void verifyTextNotPresent(final String text) throws Exception {
        verifyFalse(isTextPresent(text));
    }

    public void verifyFalse(final boolean b) {
        if (b) {
            throw new IllegalArgumentException("Verification failed.");
        }
    }

    public String getName() {
        return this.getClass().getName();
    }

    public void waitForTextPresent(final String text) throws Exception {
        while (!driver.getPageSource().contains(text)) {
            waitOneSecond();
        }

        waitOneSecond();
        setCurrentWaitTime(0);
    }

    /**
     * Wait for text to be present in the given locator.
     *
     * @param by   Finder element.
     * @param text Text to wait for.
     * @throws Exception For any test execution errors
     * @deprecated Use {@link AbstractTestWebPage#waitForTextPresent(By, String)}
     */
    public void waitForTextPresent(final By by, final String text) throws Exception {
        waitForElementPresent(by);
        while (!find(by).getText().contains(text)) {
            waitFor(500L);
        }
    }

    public Object executeJavaScript(final String javaScript) throws Exception {
        return ((JavascriptExecutor) driver).executeScript(javaScript);
    }

    public void hover(final WebElement element) throws Exception {
        final Actions action = new Actions(driver);
        action.moveToElement(element).click().build().perform();
    }

    public void waitForElementVisible(final By by) throws Exception {
        assert (waitUntil(ExpectedConditions.visibilityOfElementLocated(by)) != null);
    }

    public void waitForElementInvisible(final By by) throws Exception {
        assert (waitUntil(ExpectedConditions.invisibilityOfElementLocated(by)) != null);
    }

    public void waitForElementPresent(final By by) throws Exception {
        if (waitUntil(ExpectedConditions.presenceOfElementLocated(by)) == null) {
            fail("Could not find " + by.toString());
        }
    }

    public void waitForElementNotPresent(final By by) throws Exception {
        waitUntil(ExpectedConditions.invisibilityOfElementLocated(by));
    }

    public <V> V waitUntil(final ExpectedCondition<V> expectedCondition) throws Exception {
        final WebDriverWait webDriverWait =
            new WebDriverWait(driver, TIMEOUT_IN_SECONDS);
        return webDriverWait.until(expectedCondition);
    }

    public String getCurrentWindowHandle() throws Exception {
        return driver.getWindowHandle();
    }

    public WebDriver selectWindow(final String windowHandle) throws Exception {
        return driver.switchTo().window(windowHandle);
    }

    public void closeWindow(final String windowHandle) throws Exception {
        selectWindow(windowHandle).close();
    }

    public void waitFor(final int seconds) throws Exception {
        int count = 0;
        while (count <= seconds) {
            waitOneSecond();
            count++;
        }

        setCurrentWaitTime(0);
    }

    protected void setSeleniumServerURL(final String seleniumServerURL) {
        this.seleniumServerURL = seleniumServerURL;
    }

    public String getUsername() {
        return username;
    }

    protected void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    protected void setPassword(String password) {
        this.password = password;
    }

    public String getWebURL() {
        return webURL;
    }

    public String getEndpoint(final String defaultEndpoint) {
        return StringUtil.hasText(this.endpoint) ? this.endpoint : defaultEndpoint;
    }

    public void setWebURL(String webURL) {
        this.webURL = webURL;
    }

    public int getCurrentWaitTime() {
        return currentWaitTime;
    }

    protected void setCurrentWaitTime(final int currentWaitTime) {
        this.currentWaitTime = currentWaitTime;
    }

    /**
     * Fails a test with the given message.
     *
     * @param message Message to display explaining the failure.
     */
    public void fail(final String message) {
        throw new AssertionFailedError(message);
    }

    public boolean isFailOnTimeout() {
        return failOnTimeout;
    }

    protected void setFailOnTimeout(boolean failOnTimeout) {
        this.failOnTimeout = failOnTimeout;
    }

    /**
     * Wait one second.
     *
     * @throws Exception If anything went wrong.
     */
    public void waitOneSecond() throws Exception {
        if (isFailOnTimeout() && (getCurrentWaitTime() >= TIMEOUT_IN_MILLISECONDS)) {
            fail("Timed out.");
        } else {
            setCurrentWaitTime(getCurrentWaitTime() + 1000);
            waitFor(1000L);
        }
    }

    /**
     * Allow waiting for less than a second.
     *
     * @param milliseconds Time in milliseconds to wait.
     * @throws Exception For any test execution errors
     */
    public void waitFor(final long milliseconds) throws Exception {
        Thread.sleep(milliseconds);
    }
}
