/* 7조 (TOMTOM) - 육동현, 왕현정, 강인희 */
import java.util.Random;
import java.util.logging.Logger;
import se.sics.tasim.props.BOMBundle;
import se.sics.tasim.props.ComponentCatalog;
import se.sics.tasim.props.InventoryStatus;
import se.sics.tasim.props.OfferBundle;
import se.sics.tasim.props.OrderBundle;
import se.sics.tasim.props.RFQBundle;
import se.sics.tasim.props.SimulationStatus;
import se.sics.tasim.props.StartInfo;
import se.sics.tasim.tac03.aw.Order;
import se.sics.tasim.tac03.aw.OrderStore;
import se.sics.tasim.tac03.aw.RFQStore;
import se.sics.tasim.tac03.aw.SCMAgent;

public class ExampleAgent extends SCMAgent 
{
  // INTACT
  /** Predefined System Declared Variables */
  private static final Logger log = Logger.getLogger(ExampleAgent.class.getName());
  private Random random = new Random();
  
  /** Latest possible due date when bidding for customer orders */
  private int lastBidDueDate;
  
  /** Bookkeeper for component demand for accepted customer orders */
  private InventoryStatus componentDemand = new InventoryStatus();

  // User Definition Variables
  /** Transaction number */
  private int transCnt = 0; // transaction의 수를 카운트 - ex) offer가고 order오면 카운트 2
  
  /** Offer price discount factor when bidding for customer orders */
  private double priceDiscountFactor = 0.92; // #수익을 결정하는 핵심요소
  
  /** RFQ select control factor */
  private int basePrice; // 상품원가 (공급자로부터 구매한 부품가격)
  private int selectFilter = 400; // RFQ 걸러내는 기준  #보내는 오퍼의 개수 조절 -> 고정값으로 결정!
  
  /** Hit ratio control factor */
  private final int MAX = 320; // 매일 RFQ는 80부터 320개 정도가 발송 -> 이에 맞게 offer와 order 배열크기를 최대 320으로 잡음
  private double[] offerLog; // 보낸 오퍼관리
  private double[] orderLog; // 받은 오더관리
  private double hitRatio; // 주문성사율 = 받은 오더/보낸 오퍼
  
  /** Factory Utilization control factor */
  private double unitProdCycle; // 단위 제품 만드는데 요구되는 사이클
  private double totProdCycle; // 현재 전체 생산 사이클
  
  /** Given value from PPT */
  // 1) SKU에 따른 상품원가 - SKU #1 = 필요부품 {100, 200, 300, 400} = 1000 + 250 + 100 + 300 = 1650
  private int[] SKU_price = {-1,1650,1750,1750,1850,2150,2250,2250,2350,1650,1750,1750,1850,2150,2250,2250,2350};
  // 2) SKU에 따른 필요 사이클  - SKU #1 = 4 unitProdCycle (마찬가지로 뒤의 것도 구함)
  private int[] SKU_unitProdCycle = {-1,4,5,5,6,5,6,6,7,4,5,5,6,5,6,6,7};

  //INTACT
  /** Constructor */
  public ExampleAgent()  
  { 
	  offerLog = new double[MAX];
	  orderLog = new double[MAX];
  }
  
  /** Called when the agent received all startup information and it is time to start participating in the simulation. */
  protected void simulationStarted() 
  {
    StartInfo info = getStartInfo();
    // Calculate the latest possible due date that can be produced for and delivered in this game/simulation
    this.lastBidDueDate = info.getNumberOfDays() - 2;
  }

  /** Called when a game/simulation has ended and the agent should free its resources. */
  protected void simulationEnded() { }

  /** Called when a bundle of RFQs have been received from the customers. 
  * In TAC03 SCM the customers only send one bundle per day and the same RFQs are sent to all manufacturers.
  * @param rfqBundle a bundle of RFQs */
  protected void handleCustomerRFQs(RFQBundle rfqBundle) 
  {
	  int currentDate = getCurrentDate(); 
	  int offerQty = 0; // 받은 RFQ중 보낸 offer수를 세기 위한 변수
	  
	// 목적: 다른 Agent보다 많은 order를 따 내야 높은 수익 달성가능
	  /** Discount Factor Control */	  
	  if(transCnt >= 2) // 처음 offer와 order받는 것 2회 카운트가 필요
	  {
		// order 많으면 많은 주문 땀, offer 많으면 견적서 보냈으나 주문 못 땀
		  hitRatio = orderLog[transCnt-1]/offerLog[transCnt-2]; // hitRatio에 따라 할인율 조정
	
		  // 주문성사율이 100%이면 우리는 아쉬울게 없으므로 비싸게 받음 
	      if (hitRatio == 1.0) 
	         priceDiscountFactor += 0.03;
	      else
	      {
	         // 전략: 주문성사율이 높으면 할인율을 낮추고, 낮으면 할인율을 높이는 전략 -> 비율은 수능등급 이용
	         if(hitRatio >= 0.96)
	            priceDiscountFactor -= 0.001; // 1등급 컷
	         else if(hitRatio >= 0.89)
	            priceDiscountFactor -= 0.003; // 2등급 컷
	         else if(hitRatio >= 0.77)
	            priceDiscountFactor -= 0.010; // 3등급 컷
	         else if(hitRatio >= 0.60)
	            priceDiscountFactor -= 0.015; // 4등급 컷
	         else if(hitRatio >= 0.40)
	            priceDiscountFactor -= 0.017; // 5등급 컷
	         else if(hitRatio >= 0.23)
	            priceDiscountFactor -= 0.020; // 6등급 컷
	         else if(hitRatio >= 0.11)
	            priceDiscountFactor -= 0.025; // 7등급 컷
	         else if(hitRatio >= 0.04)
	            priceDiscountFactor -= 0.030; // 8등급 컷
	         else
	            priceDiscountFactor -= 0.050; // 9등급 컷
	      }
	  }
	
	  for (int i = 0, n = rfqBundle.size(); i < n; i++) 
	  {
		  basePrice = SKU_price[rfqBundle.getProductID(i)]; // 원가
		  unitProdCycle = SKU_unitProdCycle[rfqBundle.getProductID(i)];
			
		  int dueDate = rfqBundle.getDueDate(i); // RFQ의 납기일 파악
		  if ((dueDate - currentDate) >= 6 && (dueDate <= lastBidDueDate) && (totProdCycle <= 1990)) 
		  { 
			  int resPrice = rfqBundle.getReservePricePerUnit(i); // RFQ의 상한가 파악
			  if (resPrice >= basePrice + selectFilter) // basePrice 가격 이상만 받는다.
			  {
				  int offeredPrice = (int)(resPrice * priceDiscountFactor);
				  addCustomerOffer(rfqBundle, i, offeredPrice);
				  totProdCycle += (rfqBundle.getQuantity(i) * unitProdCycle); //제품의 unitProdCycle을 totProdCycle에 더함
				  offerQty += rfqBundle.getQuantity(i); //Offer를 보냄
			  }
		  }
	  } offerLog[transCnt] = offerQty; // 보낸 offer의 수를 저장

	  /** Controlling sending offer quantity according to factory utilization */
	  if(totProdCycle <= 1800 && selectFilter > 10)
		  selectFilter -= 10;
	  else if(totProdCycle >= 1900) 
		  selectFilter += 10;
	  totProdCycle = 0;
	  
	  sendCustomerOffers();
	  transCnt++; 
  }

  /** Called when a bundle of orders have been received from the customers. 
  * In TAC03 SCM the customers only send one order bundle per day as response to offers (and only if they want to order something).
  * @param newOrders the new customer orders */
  protected void handleCustomerOrders(Order[] newOrders) 
  {
    // Add the component demand for the new customer orders
    BOMBundle bomBundle = getBOMBundle();
	int orderQty = 0; // 구매요청수량 카운트
    for (int i = 0, n = newOrders.length; i < n; i++) 
    {
      Order order = newOrders[i];
      int productID = order.getProductID();
      int quantity = order.getQuantity();
	  orderQty += quantity;
	  
      int[] components = bomBundle.getComponentsForProductID(productID);
      if (components != null) 
    	  for (int j = 0, m = components.length; j < m; j++) 
    		  componentDemand.addInventory(components[j], quantity);
    } 
    orderLog[transCnt] = orderQty; // order에 주문수량을 넣음
	
    // Order the components needed to fulfill the new orders from the suppliers.
    ComponentCatalog catalog = getComponentCatalog();
    int currentDate = getCurrentDate();
    for (int i = 0, n = componentDemand.getProductCount(); i < n; i++) 
    {
      int quantity = componentDemand.getQuantity(i);
      if (quantity > 0) 
      {
    	  int productID = componentDemand.getProductID(i);
    	  String[] suppliers = catalog.getSuppliersForProduct(productID);
    	  if (suppliers != null) 
    	  {
    		  // Order all components from one supplier chosen by random for simplicity.
			  int supIndex = random.nextInt(suppliers.length);	  
			  addSupplierRFQ(suppliers[supIndex], productID, quantity, 0, currentDate + 2);
			  // Assume that the supplier will be able to deliver the components and remove this demand.
			  componentDemand.addInventory(productID, -quantity);
    	  } 
    	  else // There should always be suppliers for all components so this point should never be reached.
    		  log.severe("no suppliers for product " + productID);
      }
    } sendSupplierRFQs();
  }

  //INTACT
  /** Called when a bundle of offers have been received from a supplier.
  * In TAC03 SCM suppliers only send on offer bundle per day in reply to RFQs (and only if they had something to offer).
  * @param supplierAddress the supplier that sent the offers
  * @param offers a bundle of offers */
  protected void handleSupplierOffers(String supplierAddress,OfferBundle offers) 
  {
    // Earliest complete is always after partial offers 
	// so the offer bundle is traversed backwards to always accept earliest offer
    // instead of the partial (the server will ignore the second order for the same offer).
    for (int i = offers.size() - 1; i >= 0; i--) 
    {
      // Only order if quantity > 0 (otherwise it is only a price quote)
      if (offers.getQuantity(i) > 0) 
    	  addSupplierOrder(supplierAddress, offers, i);
    } sendSupplierOrders();
  }

  //INTACT
  /** Called when a simulation status has been received and that all messages from the server this day have been received. 
  * The next message will be for the next day.
  * @param status a simulation status */
  protected synchronized void handleSimulationStatus(SimulationStatus status) 
  {
    // The inventory for next day is calculated with todays deliveries and production 
	// and is changed when production and delivery requests are made.
    InventoryStatus inventory = getInventoryForNextDay();

    // Generate production and delivery schedules
    int currentDate = getCurrentDate();
    int latestDueDate = currentDate - getDaysBeforeVoid() + 2;

    OrderStore customerOrders = getCustomerOrders();
    Order[] orders = customerOrders.getActiveOrders();
    if (orders != null) 
    {
      for (int i = 0, n = orders.length; i < n; i++) 
      {
    	  Order order = orders[i];
    	  int productID = order.getProductID();
		  int dueDate = order.getDueDate();
		  int orderedQuantity = order.getQuantity();
		  int inventoryQuantity = inventory.getInventoryQuantity(productID);

		  if ((currentDate >= (dueDate - 1)) && (dueDate >= latestDueDate) && addDeliveryRequest(order)) 
		  {
			  // It was time to deliver this order and it could be delivered (the method above ensures this). 
			  // The order has automatically been marked as delivered and the products have been removed from the inventory status 
			  // (to avoid delivering the same products again).
		  } 
		  else if (dueDate <= latestDueDate) 
		  {
			  // It is too late to produce and deliver this order
			  log.info("canceling to late order " + order.getOrderID() + " (dueDate=" + order.getDueDate() + ",date=" + currentDate + ')');
			  cancelCustomerOrder(order);
		  } 
		  else if (inventoryQuantity >= orderedQuantity) 
		  {
			  // There is enough products in the inventory to fulfill this order and nothing more should be produced for it. 
			  // However to avoid reusing these products for another order they must be reserved.
			  reserveInventoryForNextDay(productID, orderedQuantity);
		  } 
		  else if (addProductionRequest(productID,orderedQuantity - inventoryQuantity)) 
		  {
			  // The method above will ensure that the needed components was avanilable 
			  // and that the factory had enough free capacity. 
			  // It also removed the needed components from the inventory status.
			  // Any existing products have been allocated to this order and must be reserved 
			  // to avoid using them in another production or delivery.
			  reserveInventoryForNextDay(productID, inventoryQuantity);
		  } 
		  else 
		  {
			  // Otherwise the production could not be done and nothing can be done for this order at this time.
			  // (lack of free factory unitProdCycles or not enough components in inventory) 
		  }
      }
    } sendFactorySchedules();
  }

  // INTACT
  /** Cancel Customer Order Method */
  private void cancelCustomerOrder(Order order) 
  {
    order.setCanceled();
    // The components for the canceled order are now available to be used in other orders.
    int[] components = getBOMBundle().getComponentsForProductID(order.getProductID());
    if (components != null) 
    {
      int quantity = order.getQuantity();
      for (int j = 0, m = components.length; j < m; j++) 
    	  componentDemand.addInventory(components[j], -quantity);
    }
  }
} // ExampleAgent