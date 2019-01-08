/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

import SatSolver.RelativeChecker;
import org.junit.Assert;
import org.junit.Test;
import static org.junit.Assert.*;

public class RelativeCheckerTest {
    
    public RelativeCheckerTest() {
    }

    /**
     * Test of main method, of class RelativeChecker.
     */
    @Test
    public void testMain() {
    }

    /**
     * Test of areRelativesCorrect method, of class RelativeChecker.
     */
    @Test
    public void testAreRelativesCorrect() {
        Assert.assertEquals(true, RelativeChecker.areRelativesCorrect("(46,44,-57,-58,-155,154,153)", "(46,44,-57,-58,-155,154,153)"));
        assertEquals(false,RelativeChecker.areRelativesCorrect("(45,-57,-58,-155,154,153)", "(45,-57,-58,-155,154,153)"));
    }
    
}
