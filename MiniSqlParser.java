import java.util.*;
import java.io.*;

public class MiniSqlParser {

  // tokens
  enum TokenType {
    IDENT, NUMBER, STRING,
    SELECT, FROM, WHERE, AND, OR, NOT, BETWEEN,
    COMMA, DOT, LPAREN, RPAREN, STAR, SEMI,
    EQ, NE, LT, LE, GT, GE,
    EOF
  }

  static class Token {
    final TokenType type;
    final String lexeme;
    Token(TokenType type, String lexeme) { this.type = type; this.lexeme = lexeme; }
    public String toString() { return type + "(" + lexeme + ")"; }
  }

  // ===== Part A: AST nodes =====
  interface AstNode { void print(String indent); }

  static class QueryNode implements AstNode {
    final AstNode selectList;
    final AstNode fromList;
    final AstNode where; // null if no WHERE clause
    QueryNode(AstNode s, AstNode f, AstNode w) { selectList=s; fromList=f; where=w; }
    public void print(String indent) {
      System.out.println(indent + "QUERY");
      selectList.print(indent + "  ");
      fromList.print(indent + "  ");
      if (where != null) {
        System.out.println(indent + "  WHERE");
        where.print(indent + "    ");
      }
    }
  }

  static class SelectListNode implements AstNode {
    final boolean star;
    final List<AstNode> attrs;
    SelectListNode(boolean star, List<AstNode> attrs) { this.star = star; this.attrs = attrs; }
    public void print(String indent) {
      System.out.println(indent + "SELECT_LIST");
      if (star) {
        System.out.println(indent + "  STAR(*)");
      } else {
        for (AstNode a : attrs) a.print(indent + "  ");
      }
    }
  }

  static class FromListNode implements AstNode {
    final List<AstNode> tables;
    FromListNode(List<AstNode> tables) { this.tables = tables; }
    public void print(String indent) {
      System.out.println(indent + "FROM_LIST");
      for (AstNode t : tables) t.print(indent + "  ");
    }
  }

  static class TableNode implements AstNode {
    final String name;
    final String alias; // optional
    TableNode(String name, String alias) { this.name = name; this.alias = alias; }
    public void print(String indent) {
      String s = alias != null ? " " + alias : "";
      System.out.println(indent + "TABLE(" + name + s + ")");
    }
  }

  // qualifier is for E.name style, null if unqualified
  static class AttrNode implements AstNode {
    final String qualifier;
    final String name;
    AttrNode(String qualifier, String name) { this.qualifier = qualifier; this.name = name; }
    public void print(String indent) {
      String label = qualifier != null ? qualifier + "." + name : name;
      System.out.println(indent + "ATTR(" + label + ")");
    }
  }

  static class NumberNode implements AstNode {
    final String value;
    NumberNode(String value) { this.value = value; }
    public void print(String indent) { System.out.println(indent + "NUM(" + value + ")"); }
  }

  static class StringNode implements AstNode {
    final String value;
    StringNode(String value) { this.value = value; }
    public void print(String indent) { System.out.println(indent + "STR(" + value + ")"); }
  }

  static class CompareNode implements AstNode {
    final String op;
    final AstNode left, right;
    CompareNode(String op, AstNode left, AstNode right) { this.op = op; this.left = left; this.right = right; }
    public void print(String indent) {
      String tmp = indent + "  ";
      System.out.println(indent + "CMP(" + op + ")");
      left.print(tmp);
      right.print(tmp);
    }
  }

  static class BetweenNode implements AstNode {
    final AstNode attr, low, high;
    BetweenNode(AstNode attr, AstNode low, AstNode high) { this.attr = attr; this.low = low; this.high = high; }
    public void print(String indent) {
      System.out.println(indent + "BETWEEN");
      attr.print(indent + "  ");
      low.print(indent + "  ");
      high.print(indent + "  ");
    }
  }

  static class AndNode implements AstNode {
    final AstNode left, right;
    AndNode(AstNode left, AstNode right) { this.left = left; this.right = right; }
    public void print(String indent) {
      System.out.println(indent + "AND");
      left.print(indent + "  ");
      right.print(indent + "  ");
    }
  }

  static class OrNode implements AstNode {
    final AstNode left, right;
    OrNode(AstNode left, AstNode right) { this.left = left; this.right = right; }
    public void print(String indent) {
      System.out.println(indent + "OR");
      left.print(indent + "  ");
      right.print(indent + "  ");
    }
  }

  static class NotNode implements AstNode {
    final AstNode child;
    NotNode(AstNode child) { this.child = child; }
    public void print(String indent) {
      System.out.println(indent + "NOT");
      child.print(indent + "  ");
    }
  }

  // ===== Part B: Lexer =====
  static List<Token> tokenize(String sql) {
    List<Token> tok = new ArrayList<>();
    int i = 0, n = sql.length();

    while (i < n) {
      char c = sql.charAt(i);

      if (Character.isWhitespace(c)) { i++; continue; }

      // single-quoted string, '' inside counts as escaped quote
      if (c == '\'') {
        StringBuilder sb = new StringBuilder();
        int start = i;
        i++;
        boolean closed = false;
        while (i < n) {
          char cur = sql.charAt(i);
          if (cur == '\'') {
            if (i + 1 < n && sql.charAt(i + 1) == '\'') { sb.append('\''); i += 2; }
            else { i++; closed = true; break; }
          } else {
            sb.append(cur);
            i++;
          }
        }
        if (!closed) throw new RuntimeException("Unterminated string literal at position " + start);
        tok.add(new Token(TokenType.STRING, sb.toString()));
        continue;
      }

      // numbers: handles 123, 3.5, and .5
      if (Character.isDigit(c) || (c == '.' && i + 1 < n && Character.isDigit(sql.charAt(i + 1)))) {
        int start = i;
        while (i < n && Character.isDigit(sql.charAt(i))) i++;
        if (i < n && sql.charAt(i) == '.') {
          i++;
          while (i < n && Character.isDigit(sql.charAt(i))) i++;
        }
        String numStr = sql.substring(start, i);
        if (numStr.length() > 0) {
          tok.add(new Token(TokenType.NUMBER, numStr));
        }
        continue;
      }

      if (Character.isLetter(c) || c == '_') {
        int start = i;
        while (i < n && (Character.isLetterOrDigit(sql.charAt(i)) || sql.charAt(i) == '_')) i++;
        String word = sql.substring(start, i);
        String kw = word.toUpperCase();
        TokenType type;
        if      (kw.equals("SELECT"))  type = TokenType.SELECT;
        else if (kw.equals("FROM"))    type = TokenType.FROM;
        else if (kw.equals("WHERE"))   type = TokenType.WHERE;
        else if (kw.equals("AND"))     type = TokenType.AND;
        else if (kw.equals("OR"))      type = TokenType.OR;
        else if (kw.equals("NOT"))     type = TokenType.NOT;
        else if (kw.equals("BETWEEN")) type = TokenType.BETWEEN;
        else                           type = TokenType.IDENT;
        tok.add(new Token(type, word));
        continue;
      }

      // check 2-char ops first so "<=" doesn't get split into "<" and "="
      if (i + 1 < n) {
        String two = sql.substring(i, i + 2);
        if (two.equals("!=") || two.equals("<>")) { tok.add(new Token(TokenType.NE, two)); i += 2; continue; }
        if (two.equals("<="))                      { tok.add(new Token(TokenType.LE, two)); i += 2; continue; }
        if (two.equals(">="))                      { tok.add(new Token(TokenType.GE, two)); i += 2; continue; }
      }

      if      (c == '=') { tok.add(new Token(TokenType.EQ,     "=")); i++; continue; }
      else if (c == '<') { tok.add(new Token(TokenType.LT,     "<")); i++; continue; }
      else if (c == '>') { tok.add(new Token(TokenType.GT,     ">")); i++; continue; }
      else if (c == ',') { tok.add(new Token(TokenType.COMMA,  ",")); i++; continue; }
      else if (c == '.') { tok.add(new Token(TokenType.DOT,    ".")); i++; continue; }
      else if (c == '(') { tok.add(new Token(TokenType.LPAREN, "(")); i++; continue; }
      else if (c == ')') { tok.add(new Token(TokenType.RPAREN, ")")); i++; continue; }
      else if (c == '*') { tok.add(new Token(TokenType.STAR,   "*")); i++; continue; }
      else if (c == ';') { tok.add(new Token(TokenType.SEMI,   ";")); i++; continue; }

      throw new RuntimeException("Unexpected character '" + c + "' at position " + i);
    }

    tok.add(new Token(TokenType.EOF, ""));
    return tok;
  }

  // ===== Part C: Parser =====
  static class Parser {
    final List<Token> tokens;
    int pos = 0;
    Parser(List<Token> tokens) { this.tokens = tokens; }

    Token peek() { return tokens.get(pos); }
    boolean match(TokenType t) { if (peek().type == t) { pos++; return true; } return false; }
    Token expect(TokenType t, String msg) {
      if (!match(t)) throw new RuntimeException("Expected " + msg + " but got " + peek());
      return tokens.get(pos-1);
    }

    QueryNode parseQuery() {
      expect(TokenType.SELECT, "SELECT");
      AstNode selectList = parseSelectList();
      expect(TokenType.FROM, "FROM");
      AstNode fromList = parseFromList();

      AstNode where = null;
      if (match(TokenType.WHERE)) where = parseExpr();
      match(TokenType.SEMI);
      expect(TokenType.EOF, "end of input");
      return new QueryNode(selectList, fromList, where);
    }

    SelectListNode parseSelectList() {
      if (match(TokenType.STAR)) return new SelectListNode(true, new ArrayList<>());
      List<AstNode> res = new ArrayList<>();
      res.add(parseAttribute());
      while (match(TokenType.COMMA)) res.add(parseAttribute());
      return new SelectListNode(false, res);
    }

    FromListNode parseFromList() {
      List<AstNode> res = new ArrayList<>();
      do {
        res.add(parseTableRef());
      } while (match(TokenType.COMMA));
      return new FromListNode(res);
    }

    // alias is optional; since keywords aren't IDENT type, this won't accidentally grab WHERE etc.
    AstNode parseTableRef() {
      Token name = expect(TokenType.IDENT, "table name");
      String alias = (peek().type == TokenType.IDENT) ? tokens.get(pos++).lexeme : null;
      return new TableNode(name.lexeme, alias);
    }

    AstNode parseExpr() { return parseOr(); }

    // OR is lowest precedence so it's outermost
    AstNode parseOr() {
      AstNode left = parseAnd();
      while (match(TokenType.OR)) left = new OrNode(left, parseAnd());
      return left;
    }

    AstNode parseAnd() {
      AstNode left = parseNot();
      while (match(TokenType.AND)) left = new AndNode(left, parseNot());
      return left;
    }

    // this was wrong before, needed to recurse so NOT NOT x works
    AstNode parseNot() {
      if (match(TokenType.NOT)) return new NotNode(parseNot());
      return parsePrimary();
    }

    AstNode parsePrimary() {
      if (match(TokenType.LPAREN)) {
        AstNode expr = parseExpr();
        expect(TokenType.RPAREN, ")");
        return expr;
      }
      return parsePredicate();
    }

    AstNode parsePredicate() {
      AstNode attr = parseAttribute();
      // BETWEEN needs to consume AND itself before parseAnd() sees it
      boolean hasBetween = match(TokenType.BETWEEN);
      if (hasBetween) {
        AstNode low = parseLiteral();
        expect(TokenType.AND, "AND");
        AstNode high = parseLiteral();
        return new BetweenNode(attr, low, high);
      }
      TokenType nextType = peek().type;
      if (isCmpOp(nextType)) {
        String op = tokens.get(pos++).lexeme;
        boolean isLiteral = peek().type == TokenType.NUMBER || peek().type == TokenType.STRING;
        AstNode right = isLiteral ? parseLiteral() : parseAttribute();
        return new CompareNode(op, attr, right);
      }
      throw new RuntimeException("Expected comparison operator or BETWEEN but got " + peek());
    }

    boolean isCmpOp(TokenType t) {
      return t == TokenType.EQ || t == TokenType.NE
          || t == TokenType.LT || t == TokenType.LE
          || t == TokenType.GT || t == TokenType.GE;
    }

    AttrNode parseAttribute() {
      Token first = expect(TokenType.IDENT, "identifier");
      if (match(TokenType.DOT)) {
        Token field = expect(TokenType.IDENT, "field name after '.'");
        return new AttrNode(first.lexeme, field.lexeme);
      }
      return new AttrNode(null, first.lexeme);
    }

    AstNode parseLiteral() {
      if (peek().type == TokenType.NUMBER) return new NumberNode(tokens.get(pos++).lexeme);
      if (peek().type == TokenType.STRING) return new StringNode(tokens.get(pos++).lexeme);
      throw new RuntimeException("Expected number or string literal but got " + peek());
    }
  }

  public static void main(String[] args) {
    // test query 4 — most complex case
    String sql = "SELECT * FROM student s WHERE (gpa >= 3.5 AND name = 'it''s ok') OR NOT (gpa < .5);";
    //String sql = "SELECT name, gpa FROM student WHERE gpa >= 3.5;";
    //String sql = "SELECT E.name, D.dname FROM Employee E, Dept D WHERE E.dept_id = D.dept_id AND E.salary BETWEEN 100000 AND 150000;";
    //String sql = "SELECT * FROM student;";
    System.out.println("input: " + sql);
    List<Token> toks = tokenize(sql);
    Parser p = new Parser(toks);
    QueryNode q = p.parseQuery();
    q.print("");
  }
}
