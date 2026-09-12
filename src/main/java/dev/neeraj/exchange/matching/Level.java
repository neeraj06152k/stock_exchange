package dev.neeraj.exchange.matching;

/* A Level is a intrusive dbly linked FIFO queue of orders of specific price & side
*/
public class Level {
    
    private final long price;

    int totalQty; // Sum of all display quantities at this price
    int orderCount; // Number of orders at this price
    private Order head; // Oldest order (front of queue, next to execute)
    private Order tail; // Newest order (back of queue)

    public Level(long price){
        this.price = price;
    }

    public void addOrder(Order order){
        order.parentLevel = this;
        this.orderCount++;
        this.totalQty += order.displayQty();

        if(head==null){
            head = order;
            tail = order;
        } else {
            order.prev = tail;
            tail.next = order;
            tail = tail.next;
        }
    }

    public void removeOrder(Order order){
        this.totalQty -= order.displayQty();
        this.orderCount--;
        if(order.prev!=null){
            order.prev.next = order.next;
        } else head = order.next;
        if(order.next!=null){
            order.next.prev = order.prev;
        } else tail = order.prev;

        order.prev = null;
        order.next = null;
        order.parentLevel = null;
    }

    public Order front() {
        return head;
    }

    public boolean isEmpty() {
        return head == null;
    }

    public long price(){
        return price;
    }

    public int totalQty(){
        return totalQty;
    }
}
