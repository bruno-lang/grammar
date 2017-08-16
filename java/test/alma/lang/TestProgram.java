package alma.lang;

import static org.junit.Assert.assertEquals;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.junit.Test;

public class TestProgram {


	@Test
	public void desugarsBareSingleRepSuffixToBlock() {
		assertDesugars(" 'xy'? ",  "(?'xy') ");
		assertDesugars(" 'xyz'* ", "(*'xyz') ");
		assertDesugars(" <'xy'+ ", "(+<'xy') ");
		assertDesugars(" 'xy'>5 ", "(5'xy'>) ");
	}

	@Test
	public void desugarsBareMultiSuffixToBlock() {
		assertDesugars(" 'xy'5+ ",  "(5+'xy') ");
		assertDesugars(" 'xyz'5* ", "(5*'xyz') ");
	}
	
	@Test
	public void desugarsBareEndSuffixToBlock() {
		assertDesugars(" 'xyz'5*", "(5*'xyz')");
	}

	@Test
	public void desugarsBareRangeSuffixToBlock() {
		assertDesugars(" 'xy'*1-4 ", "(*1-4'xy') ");
	}

	@Test
	public void desugarsBareFancyRangeSuffixToBlock() {
		assertDesugars(" 'xy'{1-4} ", "(1-4'xy') ");
	}
	
	@Test
	public void doesNotDesugarRepWithoutLeadingWS() {
		assertDesugars("'xy'5+ ",  "'xy'5+ ");
	}

	@Test
	public void desugarsMultipleBareSuffixesToBlocks() {
		assertDesugars(" 'x'+ 'y'* ", "(+'x')(*'y') ");
	}

	@Test
	public void movesBlockRepSuffixIntoBlock() {
		assertDesugars("('x')1+ ", "(1+'x') ");
		assertDesugars("('x')1+ ('y')? ", "(1+'x') (?'y') ");
	}
	
	@Test
	public void movesNestedRepSuffixIntoBlock() {
		assertDesugars("(('x')? 'y')+ ", "(+(?'x') 'y') ");
		assertDesugars("(('x' (.foo){2-5} )? 'y')+ ", "(+(?'x' (2-5.foo) ) 'y') ");
	}

	@Test
	public void desugarsBareAssignmentIntoBlock() {
		assertDesugars("foo = 'xzy' ", "(=foo 'xzy')");
		assertDesugars("foo = 'xzy'\n", "(=foo 'xzy')");
		assertDesugars("foo = 'xzy'\nbar = 'abc'\n", "(=foo 'xzy')(=bar 'abc')");
	}
	
	@Test
	public void desugarsAssignmentWithBracketsIntoBlock() {
		assertDesugars("foo = { 'xzy'}",   " (=foo 'xzy')");
		assertDesugars("foo = \t{ 'xzy'}", " (=foo 'xzy')");
	}

	@Test
	public void exampleListOfTerms() {
		assertDesugars("expr = form (, form)*  ", "(=expr form(*, form) )");
	}

	@Test
	public void exampleNestedReps() {
		assertDesugars("expr = 'x' ( (form,)+ x)* \n", "(=expr 'x'(*(+form,) x) )");
	}

	private static void assertDesugars(String before, String after) {
		assertEquals(after, new String(Program.desugar(before.getBytes()), StandardCharsets.US_ASCII));
	}
}
