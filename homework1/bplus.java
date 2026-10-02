import java.io.*;
import java.util.*;

/**
 * In-memory B+ tree index (Integer keys, Integer pointers, no duplicate keys).
 *
 * Usage:  java bplus init <d> < test.txt
 *
 * Capacity (d = the "order" parameter from the clarification):
 *   - Leaf node:     at most 2d (key, pointer) pairs
 *   - Internal node: at most 2d keys and 2d+1 children
 *   - Every non-root node holds at least d keys (half full)
 *
 * Algorithms follow Ramakrishnan & Gehrke, "Database Management Systems",
 * 3rd ed., Chapter 10:
 *   - Leaf split: the smallest key of the NEW right leaf is COPIED up.
 *   - Internal split: the middle key is PUSHED up (moved, not copied).
 *   - Delete: redistribute from a sibling if it has spare entries,
 *             otherwise merge (pulling the parent separator down for internal nodes).
 */
public class bplus {

    // ------------------------------------------------------------------
    // Output message formats (kept in one place so they are easy to change)
    // ------------------------------------------------------------------
    static String msgFound(int key, int ptr)     { return key + " found, point is " + ptr; }
    static String msgNotFound(int key)           { return key + " not found"; }
    static String msgInserted(int key, int ptr)  { return "(" + key + ", " + ptr + ") inserted"; }
    static String msgNotInserted(int key, int ptr) {
        return "(" + key + ", " + ptr + ") not inserted. " + key + " found.";
    }
    static String msgDeleted(int key)            { return key + " deleted."; }
    static String msgNotDeleted(int key)         { return key + " not found, not deleted."; }

    // ------------------------------------------------------------------
    // Node representation: one class for both leaves and internal nodes
    // ------------------------------------------------------------------
    static class Node {
        final boolean leaf;
        ArrayList<Integer> keys = new ArrayList<>();
        ArrayList<Integer> ptrs;     
        ArrayList<Node> children;     
        Node next;                   

        Node(boolean leaf) {
            this.leaf = leaf;
            if (leaf) ptrs = new ArrayList<>();
            else children = new ArrayList<>();
        }
    }

    /** Result of a node split that must be inserted into the parent. */
    static class Split {
        final int key;     
        final Node right;  
        Split(int key, Node right) { this.key = key; this.right = right; }
    }

    // ------------------------------------------------------------------
    // The B+ tree
    // ------------------------------------------------------------------
    static class BPlusTree {
        private final int d;
        private Node root;
        private final PrintWriter out;

        BPlusTree(int d, PrintWriter out) {
            this.d = d;
            this.out = out;
            this.root = new Node(true);  
        }

        // ---------------- helpers ----------------

        /** Index of the child to follow for 'key' (number of separator keys <= key). */
        private int childIndex(Node n, int key) {
            int i = 0;
            while (i < n.keys.size() && key >= n.keys.get(i)) i++;
            return i;
        }

        /** Descend from the root to the leaf that would contain 'key'. */
        private Node findLeaf(int key) {
            Node n = root;
            while (!n.leaf) n = n.children.get(childIndex(n, key));
            return n;
        }

        // ---------------- search ----------------

        /** Returns the pointer for key, or -1 if absent*/
        int search(int key) {
            Node leaf = findLeaf(key);
            int idx = Collections.binarySearch(leaf.keys, key);
            if (idx >= 0) {
                int p = leaf.ptrs.get(idx);
                out.println(msgFound(key, p));
                return p;
            }
            out.println(msgNotFound(key));
            return -1;
        }

        // ---------------- insert ----------------

        /** Inserts (key, pointer). Returns false (and prints) if the key already exists. */
        boolean insert(int key, int pointer) {
            Node leaf = findLeaf(key);
            if (Collections.binarySearch(leaf.keys, key) >= 0) {
                out.println(msgNotInserted(key, pointer));
                return false;
            }
            Split s = insertRec(root, key, pointer);
            if (s != null) {                       
                Node newRoot = new Node(false);
                newRoot.keys.add(s.key);
                newRoot.children.add(root);
                newRoot.children.add(s.right);
                root = newRoot;
            }
            out.println(msgInserted(key, pointer));
            return true;
        }

        /** Recursive insert; returns a Split if node n overflowed, else null. */
        private Split insertRec(Node n, int key, int ptr) {
            if (n.leaf) {
                int r = Collections.binarySearch(n.keys, key);
                int pos = -(r + 1);               
                n.keys.add(pos, key);
                n.ptrs.add(pos, ptr);
                if (n.keys.size() <= 2 * d) return null;

                // Overflow (2d+1 pairs): left keeps d, right gets d+1; copy up right's first key.
                Node right = new Node(true);
                List<Integer> kSub = n.keys.subList(d, n.keys.size());
                List<Integer> pSub = n.ptrs.subList(d, n.ptrs.size());
                right.keys.addAll(kSub);
                right.ptrs.addAll(pSub);
                kSub.clear();
                pSub.clear();
                right.next = n.next;
                n.next = right;
                return new Split(right.keys.get(0), right);
            }

            int i = childIndex(n, key);
            Split s = insertRec(n.children.get(i), key, ptr);
            if (s == null) return null;

            n.keys.add(i, s.key);
            n.children.add(i + 1, s.right);
            if (n.keys.size() <= 2 * d) return null;

            // Overflow (2d+1 keys)
            int up = n.keys.get(d);
            Node right = new Node(false);
            List<Integer> kSub = n.keys.subList(d + 1, n.keys.size());
            List<Node> cSub = n.children.subList(d + 1, n.children.size());
            right.keys.addAll(kSub);
            right.children.addAll(cSub);
            kSub.clear();
            cSub.clear();
            n.keys.remove(d);                    
            return new Split(up, right);
        }

        // ---------------- delete ----------------

        /** Deletes key. Returns false (and prints) if it does not exist. */
        boolean delete(int key) {
            boolean ok = deleteRec(root, key);
            if (!ok) {
                out.println(msgNotDeleted(key));
                return false;
            }
            if (!root.leaf && root.keys.isEmpty()) root = root.children.get(0);
            out.println(msgDeleted(key));
            return true;
        }

        private boolean deleteRec(Node n, int key) {
            if (n.leaf) {
                int idx = Collections.binarySearch(n.keys, key);
                if (idx < 0) return false;
                n.keys.remove(idx);                
                n.ptrs.remove(idx);
                return true;
            }
            int i = childIndex(n, key);
            Node c = n.children.get(i);
            if (!deleteRec(c, key)) return false;
            if (c.keys.size() < d) fixUnderflow(n, i);
            return true;
        }

        /** Child i of parent p has fewer than d keys: redistribute or merge. */
        private void fixUnderflow(Node p, int i) {
            Node c = p.children.get(i);
            Node left = (i > 0) ? p.children.get(i - 1) : null;
            Node right = (i < p.children.size() - 1) ? p.children.get(i + 1) : null;

            // 1) Borrow from left sibling
            if (left != null && left.keys.size() > d) {
                if (c.leaf) {
                    int k = left.keys.remove(left.keys.size() - 1);
                    int v = left.ptrs.remove(left.ptrs.size() - 1);
                    c.keys.add(0, k);
                    c.ptrs.add(0, v);
                    p.keys.set(i - 1, c.keys.get(0));
                } else {
                    c.keys.add(0, p.keys.get(i - 1));                       // separator comes down
                    c.children.add(0, left.children.remove(left.children.size() - 1));
                    p.keys.set(i - 1, left.keys.remove(left.keys.size() - 1)); // left's last key goes up
                }
                return;
            }

            // 2) Borrow from right sibling
            if (right != null && right.keys.size() > d) {
                if (c.leaf) {
                    c.keys.add(right.keys.remove(0));
                    c.ptrs.add(right.ptrs.remove(0));
                    p.keys.set(i, right.keys.get(0));
                } else {
                    c.keys.add(p.keys.get(i));    
                    c.children.add(right.children.remove(0));
                    p.keys.set(i, right.keys.remove(0));                    
                }
                return;
            }

            // 3) Merge (prefer left sibling; otherwise merge right sibling into c)
            if (left != null) merge(p, i - 1);
            else merge(p, i);
        }

        /** Merge children[idx+1] into children[idx], removing separator idx from parent. */
        private void merge(Node p, int idx) {
            Node l = p.children.get(idx);
            Node r = p.children.get(idx + 1);
            if (l.leaf) {
                l.keys.addAll(r.keys);
                l.ptrs.addAll(r.ptrs);
                l.next = r.next;
            } else {
                l.keys.add(p.keys.get(idx));       // pull separator down
                l.keys.addAll(r.keys);
                l.children.addAll(r.children);
            }
            p.keys.remove(idx);                    // int index
            p.children.remove(idx + 1);
        }

        // ---------------- range search ----------------

        /** Returns all (key, pointer) pairs with k1 <= key <= k2 (as int[]{key,ptr}), or null. */
        List<int[]> rangeSearch(int k1, int k2) {
            List<int[]> result = new ArrayList<>();
            if (k1 <= k2) {
                Node leaf = findLeaf(k1);
                boolean done = false;
                while (leaf != null && !done) {
                    for (int i = 0; i < leaf.keys.size(); i++) {
                        int k = leaf.keys.get(i);
                        if (k > k2) { done = true; break; }
                        if (k >= k1) result.add(new int[]{k, leaf.ptrs.get(i)});
                    }
                    leaf = leaf.next;
                }
            }
            if (result.isEmpty()) {
                out.println("no records in the range [" + k1 + ", " + k2 + "]");
                return null;
            }
            out.println("found");
            for (int[] kv : result) out.println("(" + kv[0] + ", " + kv[1] + ")");
            return result;
        }

        // ---------------- print / statistics ----------------

        /** Level-order dump. Leaves show (key: pointer); internal nodes show (key). */
        void print() {
            List<Node> level = new ArrayList<>();
            level.add(root);
            int lvl = 0;
            while (!level.isEmpty()) {
                StringBuilder sb = new StringBuilder("Level " + lvl + ": ");
                List<Node> nextLevel = new ArrayList<>();
                boolean firstNode = true;
                for (Node n : level) {
                    if (!firstNode) sb.append(' ');
                    firstNode = false;
                    sb.append('[');
                    for (int i = 0; i < n.keys.size(); i++) {
                        if (i > 0) sb.append(' ');
                        sb.append('(').append(n.keys.get(i));
                        if (n.leaf) sb.append(": ").append(n.ptrs.get(i));
                        sb.append(')');
                    }
                    sb.append(']');
                    if (!n.leaf) nextLevel.addAll(n.children);
                }
                out.println(sb);
                level = nextLevel;
                lvl++;
            }
        }

        /** Height (number of levels), total nodes, total keys. */
        void printStatistics() {
            int height = 0, nodes = 0, keys = 0;
            List<Node> level = new ArrayList<>();
            level.add(root);
            while (!level.isEmpty()) {
                height++;
                List<Node> nextLevel = new ArrayList<>();
                for (Node n : level) {
                    nodes++;
                    if (n.leaf) keys += n.keys.size();
                    else nextLevel.addAll(n.children);
                }
                level = nextLevel;
            }
            out.println("Tree Height: " + height);
            out.println("Total Nodes: " + nodes);
            out.println("Total Keys: " + keys);
        }
    }

    // ------------------------------------------------------------------
    // Command interpreter
    // ------------------------------------------------------------------
    public static void main(String[] args) throws IOException {
        // Parse "init <d>" (also tolerate a bare "<d>").
        int d = -1;
        try {
            if (args.length >= 2 && args[0].equalsIgnoreCase("init")) d = Integer.parseInt(args[1].trim());
            else if (args.length == 1) d = Integer.parseInt(args[0].trim());
        } catch (NumberFormatException e) {
            d = -1;
        }
        if (d < 1) {
            System.err.println("Usage: java bplus init <d> < test.txt   (d >= 1)");
            System.exit(1);
        }

        PrintWriter out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(System.out)));
        BPlusTree tree = new BPlusTree(d, out);
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));

        String line;
        while ((line = in.readLine()) != null) {
            // Accept both "SEARCH 10" and "search(10)" / "INSERT(10, 5)" styles.
            line = line.replace('(', ' ').replace(')', ' ').replace(',', ' ').trim();
            if (line.isEmpty()) continue;
            String[] t = line.split("\\s+");
            String cmd = t[0].toUpperCase();
            try {
                switch (cmd) {
                    case "INSERT":
                        tree.insert(Integer.parseInt(t[1]), Integer.parseInt(t[2]));
                        break;
                    case "DELETE":
                        tree.delete(Integer.parseInt(t[1]));
                        break;
                    case "SEARCH":
                        tree.search(Integer.parseInt(t[1]));
                        break;
                    case "RANGESEARCH":
                        tree.rangeSearch(Integer.parseInt(t[1]), Integer.parseInt(t[2]));
                        break;
                    case "PRINT":
                        if (t.length > 1 && t[1].equalsIgnoreCase("STATISTICS")) tree.printStatistics();
                        else tree.print();
                        break;
                    case "PRINTSTATISTICS":
                        tree.printStatistics();
                        break;
                    default:
                        System.err.println("Unknown command: " + line);
                }
            } catch (ArrayIndexOutOfBoundsException | NumberFormatException e) {
                System.err.println("Bad command: " + line);
            }
        }
        out.flush();
    }
}