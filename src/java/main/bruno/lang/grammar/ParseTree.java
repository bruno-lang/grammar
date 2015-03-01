package bruno.lang.grammar;

import java.io.PrintStream;
import java.util.Arrays;

import bruno.lang.grammar.Grammar.Rule;


/**
 * A parse tree as a sequence of tokens for a particular {@link Grammar} a.k.a.
 * index overlay parse tree.
 * 
 * @author jan
 */
public final class ParseTree {

	private final Rule[] rules;
	private final int[] starts;
	private final int[] ends;
	private final int[] levels;
	
	private final int[] indexStack = new int[50];
	
	private int level = -1;
	private int top = -1;
	
	public ParseTree(int length) {
		super();
		this.rules = new Rule[length];
		this.starts = new int[length];
		this.ends = new int[length];
		this.levels = new int[length];
	}
	
	private ParseTree(Rule[] rules, int[] starts, int[] ends, int[] levels,
			int level, int top) {
		super();
		this.rules = rules;
		this.starts = starts;
		this.ends = ends;
		this.levels = levels;
		this.level = level;
		this.top = top;
	}

	public void push(Rule rule, int start) {
		starts[++top] = start;
		ends[top] = start;
		rules[top] = rule;
		level++;
		levels[top] = level;
		indexStack[level] = top;
	}
	
	public int end(int index) {
		return ends[index];
	}
	
	public int start(int index) {
		return starts[index];
	}
	
	public int level(int index) {
		return levels[index];
	}
	
	public Rule rule(int index) {
		return rules[index];
	}
	
	public int end() {
		return ends[0];
	}
	
	public int count() {
		return top+1;
	}

	public void pop() {
		top = indexStack[level]-1;
		level--;
	}

	public void done(int end) {
		ends[indexStack[level]] = end;
		level--;
	}
	
	public void erase(int position) {
		while (ends[top] > position) {
			top--;
		}
	}

	// ------------------------------------------------------------------------
	// everything below is not essential for the tree but used for error handling
	// ------------------------------------------------------------------------
	
	/*
	 * printing... 
	 */
	
	public void print(int position, PrintStream out) {
		int index = tokenIndexFor(position);
		StringBuilder b = new StringBuilder();
		toString(b, "", index);
		out.println(b);
	}
	
	@Override
	public String toString() {
		if (top < 0)
			return "(empty)";
		StringBuilder b = new StringBuilder();
		for (int i = 0; i <= top; i++) {
			toString(b, "", i);
		}
		return b.toString();
	}
	
	private void toString(StringBuilder b, String indent, int index) {
		char[] ind = new char[Math.abs(levels[index])];
		Arrays.fill(ind, ' ');
		b.append(String.format("%4s ", index)).append(ind).append(rules[index].name).append(' ').append(starts[index]).append(':').append(ends[index]).append('\n');
	}
	
	/*
	 * further processing utility functions below.
	 */
	
	private int tokenIndexFor(int position) {
		int index = Math.abs(Arrays.binarySearch(starts, 0, top, position));
		while (index > 0 && starts[index] > position) {
			index--;
		}
		return index;
	}
	
	public int next(int index) {
		final int l = level(index);
		final int c = count();
		while (index < c) {
			if (level(++index) <= l) {
				return index;
			}
		}
		return c;
	}
	
	public ParseTree debug() {
		int t = 0;
		while (rules[t] != null) { t++; }
		return new ParseTree(rules, starts, ends, levels, 0, t-1);
	}

	public boolean isSequential() {
		return starts[1] == ends[0];
	}
	
	public ParseTree sequential() {
		if (isSequential()) {
			return this;
		}
		ParseTree l = new ParseTree(rules.length);
		sequential(l, 0);
		return l;
	}
	
	private int sequential(ParseTree dest, final int index) {
		int i = index;
		final int level = level(i);
		final int nextLevel = level+1; // the level we are looking for
		final int count = count();
		i++;
		if (i >= count || level(i) <= level) {
			dest.sequentialPush(rule(index), level, start(index), end(index));
			return i;
		}
		int start = start(index);
		while (i < count && level(i) == nextLevel) {
			int s = start(i);
			if (s > start) {
				dest.sequentialPush(rule(index), -level(index), start, s);
			}
			i = sequential(dest, i);
			start = dest.ends[dest.top];
		}
		int end = end(index);
		if (end > start) {
			dest.sequentialPush(rule(index), -level, start, end);
		}
		return i;
	}
	
	private void sequentialPush(Rule rule, int level, int start, int end) {
		rules[++top] = rule;
		levels[top] = level;
		starts[top] = start;
		ends[top] = end;
	}
}
