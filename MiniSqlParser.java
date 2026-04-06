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
    // TODO: implement tokenizer
    return List.of(new Token(TokenType.EOF, "EOF"));
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
    // Manually-built AST for:
    // SELECT * FROM student s WHERE (gpa >= 3.5 AND name = 'it''s ok') OR NOT (gpa < .5);
    AstNode where = new OrNode(
      new AndNode(
        new CompareNode(">=", new AttrNode(null, "gpa"), new NumberNode("3.5")),
        new CompareNode("=",  new AttrNode(null, "name"), new StringNode("it's ok"))
      ),
      new NotNode(
        new CompareNode("<", new AttrNode(null, "gpa"), new NumberNode(".5"))
      )
    );

    QueryNode q = new QueryNode(
      new SelectListNode(true, List.of()),
      new FromListNode(List.of(new TableNode("student", "s"))),
      where
    );

    q.print("");
  }
}
