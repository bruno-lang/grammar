package bruno.lang.grammar;

import static java.nio.ByteBuffer.wrap;
import static org.junit.Assert.assertEquals;

import java.nio.ByteBuffer;
import java.util.Arrays;

import org.junit.Test;

public class TestBParser {

	@Test
	public void justOneLiteral() {
		String data = "aabbcc";
		String lang =
				w("v1")+
				w("#-aabbcc")+
				w("'-"+i(1))
		;
		int pN = BParser.parse(0, wrap(data.getBytes()), 64, wrap(lang.getBytes()));
		assertEquals(6, pN);
	}
	
	@Test
	public void repeatingOneLiteral() {
		String data = "abcabcabcabc";
		String lang =
				w("v1")+
				w("#-abc")+
				w("'-"+i(1))+
				w("*-"+i(2)+i(0)+i(5))
		;
		int pN = BParser.parse(0, wrap(data.getBytes()), 96, wrap(lang.getBytes()));
		assertEquals(12, pN);
	}
	
	@Test
	public void sequenceOfTwoLiterals() {
		String data = "abcdef";
		ByteBuffer lang = ByteBuffer.allocate(194)
				.put(w("v1").getBytes())
				.put(w("#-abc").getBytes())    // 1
				.put(w("#-def").getBytes())    // 2
				.put("'-".getBytes()).putShort((short) 1).put(zeros(28)) // 3
				.put("'-".getBytes()).putShort((short) 2).put(zeros(28)) // 4
				.put("&-".getBytes()).putShort((short) 3).putShort((short) 4).put(zeros(26));
		;
		int pN = BParser.parse(0, wrap(data.getBytes()), 160, lang);
		assertEquals(6, pN);
	}
	
	@Test
	public void alternativesOfTwoLiterals() {
		ByteBuffer lang = ByteBuffer.allocate(194)
				.put(w("v1").getBytes())
				.put(w("#-abc").getBytes())    // 1
				.put(w("#-def").getBytes())    // 2
				.put("'-".getBytes()).putShort((short) 1).put(zeros(28)) // 3
				.put("'-".getBytes()).putShort((short) 2).put(zeros(28)) // 4
				.put("|-".getBytes()).putShort((short) 3).putShort((short) 4).put(zeros(26));
		;
		int pN = BParser.parse(0, wrap("abc".getBytes()), 160, lang);
		assertEquals(3, pN);
		pN = BParser.parse(0, wrap("def".getBytes()), 160, lang);
		assertEquals(3, pN);
	}
	
	@Test
	public void charactersetIncludingTwoCharacters() {
		byte[] set = makeSet('c', '?');
		ByteBuffer lang = ByteBuffer.allocate(64)
				.put(w("v1").getBytes())
				.put("_-".getBytes()).put(set);
		int pN = BParser.parse(0, wrap("c".getBytes()), 32, lang);
		assertEquals(1, pN);
		pN = BParser.parse(0, wrap("?".getBytes()), 32, lang);
		assertEquals(1, pN);
		pN = BParser.parse(0, wrap("b".getBytes()), 32, lang);
		assertEquals(-1, pN);
	}

	@Test
	public void whitespaceMayBeIndent() {
		String data = " \t\n";
		String lang =
				w("v1")+
				w(",")
		;
		int pN = BParser.parse(0, wrap(data.getBytes()), 32, wrap(lang.getBytes()));
		assertEquals(2, pN);
	}

	@Test
	public void whitespaceMustBeIndent() {
		String data = " \t\n";
		String lang =
				w("v1")+
				w(";")
		;
		int pN = BParser.parse(0, wrap(data.getBytes()), 32, wrap(lang.getBytes()));
		assertEquals(2, pN);
	}
	
	@Test
	public void whitespaceMayBeWS() {
		String data = " \t\n";
		String lang =
				w("v1")+
				w(".")
		;
		int pN = BParser.parse(0, wrap(data.getBytes()), 32, wrap(lang.getBytes()));
		assertEquals(3, pN);
	}
	
	@Test
	public void whitespaceMustBeWS() {
		String data = " \t\n";
		String lang =
				w("v1")+
				w(":")
		;
		int pN = BParser.parse(0, wrap(data.getBytes()), 32, wrap(lang.getBytes()));
		assertEquals(3, pN);
	}
	
	@Test
	public void whitespaceMustBeLineWrap() {
		String data = " \t\n";
		String lang =
				w("v1")+
				w("!")
		;
		int pN = BParser.parse(0, wrap(data.getBytes()), 32, wrap(lang.getBytes()));
		assertEquals(3, pN);
	}
	
	@Test
	public void sequenceLiteralWSRepetitionOfCharacterset() {
		String data = "keyword  x~zz~";
		ByteBuffer lang = ByteBuffer.allocate(32 * 7)
				.put(w("v1").getBytes())
				.put(w("#-keyword").getBytes())
				.put("'-".getBytes()).putShort((short) 1).put(zeros(28))
				.put("_-".getBytes()).put(makeSet('x', 'z', '0', '~')).put(zeros(14))
				.put(w("*-"+i(3)+i(0)+i(10)).getBytes())
				.put(w("&-"+i(2)+",-"+i(4)).getBytes())
		;
		int pN = BParser.parse(0, wrap(data.getBytes()), 160, lang);
		assertEquals(14, pN);
	}

	private static byte[] makeSet(char... members) {
		byte[] set = new byte[16];
		for (char c : members) {
			addTo(set, c);
		}
		return set;
	}
	
	private static void addTo(byte[] set, int index) {
		set[index/8] |= 1 << (index % 8);
	}
	
	private static byte[] zeros(int length) {
		return new byte[length];
	}

	private static String i(int index) {
		return new String(ByteBuffer.allocate(2).putShort((short) index).array(), 0, 2);
	}
	
	private static String w(String w) {
		return new String(Arrays.copyOf(w.getBytes(), 32));
	}
}