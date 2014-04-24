package bruno.lang.grammar;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;

import bruno.lang.grammar.Grammar.Rule;
import bruno.lang.grammar.Grammar.RuleType;

/**
 * Builds a {@link Grammar} from a given {@link Parsed} grammar file.
 *  
 * @author jan
 */
public final class Builder {

	/**
	 * Used to indicate the distinct from index as a {@link Rule} that is
	 * detected by its identity. It has just this workaround helper
	 * functionality within the builder.
	 */
	private static final Rule DISTINCT_FROM = Rule.seq();
	
	public static Rule[] grammar(Parsed grammar) {
		final List<Rule> rules = new ArrayList<>();
		final ParseTree tokens = grammar.tree;
		final int c = tokens.count();
		int token = 0;
		while (token < c) {
			Rule r = tokens.rule(token);
			if (r == Lingukit.grammar) {
				token++;
			} else if (r == Lingukit.member) {
				if (tokens.rule(token+1) == Lingukit.rule) {
					Rule rule = rule(token+1, grammar);
					rules.add(rule);
				}
				token = tokens.next(token);
			}
		}
		return rules.toArray(new Rule[0]);
	}

	private static Rule rule(int token, Parsed grammar) {
		check(token, grammar, Lingukit.rule);
		return selection(token+2, grammar).as(grammar.text(token+1));
	}

	private static Rule selection(int token, Parsed grammar) {
		check(token, grammar, Lingukit.selection);
		final List<Rule> alternatives = new ArrayList<>();
		final ParseTree tokens = grammar.tree;
		final int end = tokens.end(token)+1;
		int i = token+1;
		while (tokens.rule(i) == Lingukit.sequence && tokens.end(i) <= end) {
			alternatives.add(sequence(i, grammar));
			i = tokens.next(i);
		}
		if (alternatives.size() == 1) {
			return alternatives.get(0);
		}
		return Rule.selection(alternatives.toArray(new Rule[0]));
	}

	private static Rule sequence(int token, Parsed grammar) {
		check(token, grammar, Lingukit.sequence);
		final List<Rule> elems = new ArrayList<>();
		final ParseTree tokens = grammar.tree;
		final int end = tokens.end(token)+1;
		int distinctFrom = Rule.UNDISTINGUISHABLE;
		int i = token+1;
		while (tokens.rule(i) == Lingukit.element && tokens.end(i) <= end) {
			Rule e = element(i, grammar);
			if (e != DISTINCT_FROM) {
				elems.add(e);
			} else {
				distinctFrom = elems.size();
			}
			i = tokens.next(i);
		}
		if (elems.size() == 1) {
			return elems.get(0);
		}
		return Rule.seq(elems.toArray(new Rule[0])).distinctFrom(distinctFrom);
	}

	private static Rule element(int token, Parsed grammar) {
		check(token, grammar, Lingukit.element);
		final ParseTree tokens = grammar.tree;
		Occur occur = occur(tokens.next(token+1), grammar, token);
		Rule r = tokens.rule(token+1);
		if (r == Lingukit.distinction) {
			return DISTINCT_FROM;
		}
		if (r == Lingukit.completion) {
			return Rule.completion();
		}
		if (r == Lingukit.group) {
			return capture(tokens.next(token+2), grammar, selection(token+2, grammar)).occurs(occur);
		}
		if (r == Lingukit.option) {
			return capture(tokens.next(token+2), grammar, selection(token+2, grammar)).occurs(Occur.qmark);
		}
		if (r == Lingukit.terminal) {
			Rule t = terminal(token+1, grammar).occurs(occur);
			// a terminal of a single character -> use literal instead
			if (t.type == RuleType.TERMINAL && t.terminal.isSingleCharacter() && t.terminal.ranges[0] >= 0) { 
				return Rule.string(new String(UTF8.bytes(t.terminal.ranges[0]))).occurs(occur);
			}
			return t;
		}
		if (r == Lingukit.string) {
			String text = grammar.text(token+1);
			return Rule.string(text.substring(1, text.length()-1)).occurs(occur);
		}
		if (r == Lingukit.ref) {
			return ref(token+1, grammar).occurs(occur);
		}
		throw unexpectedRule(r);
	}

	private static Rule ref(int token, Parsed grammar) {
		return capture(token+2, grammar, Rule.ref(grammar.text(token+1)));
	}

	private static Rule capture(int token, Parsed grammar, Rule rule) {
		if (grammar.tree.rule(token) == Lingukit.capture) {
			return rule.as(grammar.text(token+1));
		}
		return rule;
	}

	private static Rule terminal(int token, Parsed grammar) {
		check(token, grammar, Lingukit.terminal);
		Rule r = grammar.tree.rule(token+1);
		if (r == Lingukit.ranges) {
			return ranges(token+1, grammar);
		}
		if (r == Lingukit.figures) {
			return figures(token+1, grammar);
		}
		if (r == Lingukit.pattern) {
			return pattern(token+1, grammar);
		}
		throw unexpectedRule(r);
	}

	private static Rule pattern(int token, Parsed grammar) {
		check(token, grammar, Lingukit.pattern);
		boolean not = grammar.tree.rule(token+1) == Lingukit.not;
		Rule p = patternSelection(token+(not?2:1), grammar);
		return not ? Rule.pattern(Patterns.not(p.pattern)) : p;
	}

	private static Rule patternSelection(int token, Parsed grammar) {
		Rule r = grammar.tree.rule(token);
		if (r == Lingukit.gap) {
			return Rule.pattern(Patterns.GAP);
		}
		if (r == Lingukit.pad) {
			return Rule.pattern(Patterns.PAD);
		}
		if (r == Lingukit.indent) {
			return Rule.pattern(Patterns.INDENT);
		}
		if (r == Lingukit.separator) {
			return Rule.pattern(Patterns.SEPARATOR);
		}
		if (r == Lingukit.wrap) {
			return Rule.pattern(Patterns.WRAP);
		}
		throw unexpectedRule(r);
	}

	private static Rule figures(int token, Parsed grammar) {
		check(token, grammar, Lingukit.figures);
		final ParseTree tokens = grammar.tree;
		final int end = tokens.end(token);
		Terminal terminal = null;
		int i = token+1;
		List<Rule> refs = new ArrayList<>();
		while (tokens.end(i) <= end && tokens.rule(i) != Lingukit.capture) {
			Rule figure = tokens.rule(i);
			if (figure == Lingukit.ranges) {
				Rule ranges = ranges(i, grammar);
				terminal = terminal == null ? ranges.terminal : terminal.and(ranges.terminal);
			} else if (figure == Lingukit.name) {
				String name = grammar.text(i);
				if (name.charAt(0) != '-') {
					name = "-"+name; // always do not capture these
				}
				refs.add(Rule.ref(name));
			}
			i = tokens.next(i);
		}
		Rule r = terminal == null ? Rule.selection(refs.toArray(new Rule[0])) : Rule.terminal(terminal);
		if (!refs.isEmpty() && terminal != null) {
			Rule[] a = Arrays.copyOf(refs.toArray(new Rule[0]), refs.size() + 1);
			a[a.length-1] = r;
			r = Rule.selection(a);
		}
		return capture(i, grammar, r);
	}

	private static Rule ranges(int token, Parsed grammar) {
		check(token, grammar, Lingukit.ranges);
		boolean not = grammar.tree.rule(token+1) == Lingukit.not;
		Rule ranges = rangesSelection(token +(not ? 2 : 1), grammar);
		return not ? Rule.terminal(ranges.terminal.not()) : ranges;
	}

	private static Rule rangesSelection(int token, Parsed grammar) {
		Rule r = grammar.tree.rule(token);
		if (r == Lingukit.wildcard) {
			return Rule.terminal(Terminal.WILDCARD);
		}
		if (r == Lingukit.letter) {
			return Rule.terminal(Terminal.LETTERS);
		}
		if (r == Lingukit.upper) {
			return Rule.terminal(Terminal.UPPER_LETTERS);
		}
		if (r == Lingukit.lower) {
			return Rule.terminal(Terminal.LOWER_LETTERS);
		}
		if (r == Lingukit.hex) {
			return Rule.terminal(Terminal.HEX_NUMBER);
		}
		if (r == Lingukit.octal) {
			return Rule.terminal(Terminal.OCTAL_NUMBER);
		}
		if (r == Lingukit.binary) {
			return Rule.terminal(Terminal.BINARY_NUMBER);
		}
		if (r == Lingukit.digit) {
			return Rule.terminal(Terminal.DIGITS);
		}
		if (r == Lingukit.category) {
			//TODO
			throw new UnsupportedOperationException("Not available yet");
		}
		if (r == Lingukit.range) {
			return Rule.terminal(Terminal.range(literal(token+1, grammar), literal(token+3, grammar)));
		}
		if (r == Lingukit.literal) {
			return Rule.terminal(Terminal.character(literal(token, grammar)));
		}
		if (r == Lingukit.whitespace) {
			return Rule.terminal(Terminal.WHITESPACE);
		}
		if (r == Lingukit.shortname) {
			String name = grammar.text(token+1);
			int c = name.charAt(1);
			if (c == 't') {
				return Rule.terminal(Terminal.character('\t'));
			}
			if (c == 'n') {
				return Rule.terminal(Terminal.character('\n'));
			}
			if (c == 'r') {
				return Rule.terminal(Terminal.character('\r'));
			}
			throw new NoSuchElementException(name);
		}
		throw unexpectedRule(r);
	}

	private static int literal(int token, Parsed grammar) {
		check(token, grammar, Lingukit.literal);
		Rule r = grammar.tree.rule(token+1);
		if (r == Lingukit.symbol) {
			return grammar.text(token+1).codePointAt(1);
		}
		if (r == Lingukit.code_point) {
			return Integer.parseInt(grammar.text(token+1).substring(2), 16);
		}
		throw unexpectedRule(r);
	}

	private static Occur occur(int token, Parsed grammar, int parent) {
		// there might not be an occurrence token or it belongs to a outer parent 
		if (grammar.tree.rule(token) != Lingukit.occurrence || grammar.tree.end(parent) < grammar.tree.end(token)) {
			return Occur.once;
		}
		Rule occur = grammar.tree.rule(token+1);
		if (occur == Lingukit.plus) {
			return Occur.plus;
		}
		if (occur == Lingukit.star) {
			return Occur.star;
		}
		if (occur == Lingukit.qmark) {
			return Occur.qmark;
		}
		int min = Integer.parseInt(grammar.text(token+1));
		int max = min;
		if ("to".equals(grammar.tree.rule(token+2).name)) {
			max = Occur.plus.max;
			if ("max".equals(grammar.tree.rule(token+3).name)) {
				max = Integer.parseInt(grammar.text(token+3));
			}
		}
		return Occur.occur(min, max);
	}
	
	private static RuntimeException unexpectedRule(Rule r) {
		return new RuntimeException("Unexpected rule: "+r);
	}
	
	private static void check(int token, Parsed grammar, Rule expected) {
		if (grammar.tree.rule(token) != expected) {
			throw new RuntimeException("expected "+expected+" but got: "+grammar.tree.rule(token));
		}
	}
}