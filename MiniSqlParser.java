import java.util.*;

public class MiniSqlParser {

  // ===== Token =====
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

  // ===== AST =====
  interface AstNode { void print(String indent); }

  static class QueryNode implements AstNode {
    final AstNode selectList;
    final AstNode fromList;
    final AstNode where; // may be null
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

  // ===== AST Nodes =====

  static class SelectListNode implements AstNode {
    final boolean star;
    final List<AstNode> attrs; // empty when star == true
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
    final String alias; // may be null
    TableNode(String name, String alias) { this.name = name; this.alias = alias; }
    public void print(String indent) {
      System.out.println(indent + "TABLE(" + name + (alias != null ? " " + alias : "") + ")");
    }
  }

  static class AttrNode implements AstNode {
    final String qualifier; // may be null
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
      System.out.println(indent + "CMP(" + op + ")");
      left.print(indent + "  ");
      right.print(indent + "  ");
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

  // ===== Lexer =====
  static List<Token> tokenize(String sql) {
    List<Token> tokens = new ArrayList<>();
    int i = 0, n = sql.length();

    while (i < n) {
      char c = sql.charAt(i);

      // whitespace
      if (Character.isWhitespace(c)) { i++; continue; }

      // string literal '...' with '' as escaped quote
      if (c == '\'') {
        StringBuilder sb = new StringBuilder();
        i++; // skip opening quote
        while (i < n) {
          if (sql.charAt(i) == '\'') {
            if (i + 1 < n && sql.charAt(i + 1) == '\'') { sb.append('\''); i += 2; }
            else { i++; break; } // closing quote
          } else {
            sb.append(sql.charAt(i++));
          }
        }
        tokens.add(new Token(TokenType.STRING, sb.toString()));
        continue;
      }

      // number: 123, 3.5, .5
      if (Character.isDigit(c) || (c == '.' && i + 1 < n && Character.isDigit(sql.charAt(i + 1)))) {
        int start = i;
        while (i < n && Character.isDigit(sql.charAt(i))) i++;
        if (i < n && sql.charAt(i) == '.') {
          i++;
          while (i < n && Character.isDigit(sql.charAt(i))) i++;
        }
        tokens.add(new Token(TokenType.NUMBER, sql.substring(start, i)));
        continue;
      }

      // identifier or keyword (case-insensitive keyword match)
      if (Character.isLetter(c) || c == '_') {
        int start = i;
        while (i < n && (Character.isLetterOrDigit(sql.charAt(i)) || sql.charAt(i) == '_')) i++;
        String word = sql.substring(start, i);
        String upper = word.toUpperCase();
        TokenType type;
        if      (upper.equals("SELECT"))  type = TokenType.SELECT;
        else if (upper.equals("FROM"))    type = TokenType.FROM;
        else if (upper.equals("WHERE"))   type = TokenType.WHERE;
        else if (upper.equals("AND"))     type = TokenType.AND;
        else if (upper.equals("OR"))      type = TokenType.OR;
        else if (upper.equals("NOT"))     type = TokenType.NOT;
        else if (upper.equals("BETWEEN")) type = TokenType.BETWEEN;
        else                              type = TokenType.IDENT;
        tokens.add(new Token(type, word));
        continue;
      }

      // two-character operators
      if (i + 1 < n) {
        String two = sql.substring(i, i + 2);
        if (two.equals("!=") || two.equals("<>")) { tokens.add(new Token(TokenType.NE, two)); i += 2; continue; }
        if (two.equals("<="))                      { tokens.add(new Token(TokenType.LE, two)); i += 2; continue; }
        if (two.equals(">="))                      { tokens.add(new Token(TokenType.GE, two)); i += 2; continue; }
      }

      // single-character tokens
      if      (c == '=') { tokens.add(new Token(TokenType.EQ,     "=")); i++; continue; }
      else if (c == '<') { tokens.add(new Token(TokenType.LT,     "<")); i++; continue; }
      else if (c == '>') { tokens.add(new Token(TokenType.GT,     ">")); i++; continue; }
      else if (c == ',') { tokens.add(new Token(TokenType.COMMA,  ",")); i++; continue; }
      else if (c == '.') { tokens.add(new Token(TokenType.DOT,    ".")); i++; continue; }
      else if (c == '(') { tokens.add(new Token(TokenType.LPAREN, "(")); i++; continue; }
      else if (c == ')') { tokens.add(new Token(TokenType.RPAREN, ")")); i++; continue; }
      else if (c == '*') { tokens.add(new Token(TokenType.STAR,   "*")); i++; continue; }
      else if (c == ';') { tokens.add(new Token(TokenType.SEMI,   ";")); i++; continue; }

      throw new RuntimeException("Unexpected character '" + c + "' at position " + i);
    }

    tokens.add(new Token(TokenType.EOF, ""));
    return tokens;
  }

  // ===== Parser =====
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
      // TODO: implement using grammar
      return null;
    }

    // TODO: parseSelectList, parseFromList, parseExpr, parseOr, parseAnd, parseNot, parsePrimary, parsePredicate, parseAttribute, parseLiteral
  }

  public static void main(String[] args) {
    String sql = """
      SELECT *
      FROM student s
      WHERE (gpa >= 3.5 AND name = 'it''s ok') OR NOT (gpa < .5);
      """;
    List<Token> toks = tokenize(sql);
    Parser p = new Parser(toks);
    QueryNode q = p.parseQuery();
    q.print("");
  }
}
