/* 7�� (TOMTOM) - ������, ������, ������ */
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
  private int transCnt = 0; // transaction�� ���� ī��Ʈ - ex) offer���� order���� ī��Ʈ 2
  
  /** Offer price discount factor when bidding for customer orders */
  private double priceDiscountFactor = 0.92; // #������ �����ϴ� �ٽɿ��
  
  /** RFQ select control factor */
  private int basePrice; // ��ǰ���� (�����ڷκ��� ������ ��ǰ����)
  private int selectFilter = 400; // RFQ �ɷ����� ����  #������ ������ ���� ���� -> ���������� ����!
  
  /** Hit ratio control factor */
  private final int MAX = 320; // ���� RFQ�� 80���� 320�� ������ �߼� -> �̿� �°� offer�� order �迭ũ�⸦ �ִ� 320���� ����
  private double[] offerLog; // ���� ���۰���
  private double[] orderLog; // ���� ��������
  private double hitRatio; // �ֹ������� = ���� ����/���� ����
  
  /** Factory Utilization control factor */
  private double unitProdCycle; // ���� ��ǰ ����µ� �䱸�Ǵ� ����Ŭ
  private double totProdCycle; // ���� ��ü ���� ����Ŭ
  
  /** Given value from PPT */
  // 1) SKU�� ���� ��ǰ���� - SKU #1 = �ʿ��ǰ {100, 200, 300, 400} = 1000 + 250 + 100 + 300 = 1650
  private int[] SKU_price = {-1,1650,1750,1750,1850,2150,2250,2250,2350,1650,1750,1750,1850,2150,2250,2250,2350};
  // 2) SKU�� ���� �ʿ� ����Ŭ  - SKU #1 = 4 unitProdCycle (���������� ���� �͵� ����)
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
	  int offerQty = 0; // ���� RFQ�� ���� offer���� ���� ���� ����
	  
	// ����: �ٸ� Agent���� ���� order�� �� ���� ���� ���� �޼�����
	  /** Discount Factor Control */	  
	  if(transCnt >= 2) // ó�� offer�� order�޴� �� 2ȸ ī��Ʈ�� �ʿ�
	  {
		// order ������ ���� �ֹ� ��, offer ������ ������ �������� �ֹ� �� ��
		  hitRatio = orderLog[transCnt-1]/offerLog[transCnt-2]; // hitRatio�� ���� ������ ����
	
		  // �ֹ��������� 100%�̸� �츮�� �ƽ���� �����Ƿ� ��ΰ� ���� 
	      if (hitRatio == 1.0) 
	         priceDiscountFactor += 0.03;
	      else
	      {
	         // ����: �ֹ��������� ������ �������� ���߰�, ������ �������� ���̴� ���� -> ������ ���ɵ�� �̿�
	         if(hitRatio >= 0.96)
	            priceDiscountFactor -= 0.001; // 1��� ��
	         else if(hitRatio >= 0.89)
	            priceDiscountFactor -= 0.003; // 2��� ��
	         else if(hitRatio >= 0.77)
	            priceDiscountFactor -= 0.010; // 3��� ��
	         else if(hitRatio >= 0.60)
	            priceDiscountFactor -= 0.015; // 4��� ��
	         else if(hitRatio >= 0.40)
	            priceDiscountFactor -= 0.017; // 5��� ��
	         else if(hitRatio >= 0.23)
	            priceDiscountFactor -= 0.020; // 6��� ��
	         else if(hitRatio >= 0.11)
	            priceDiscountFactor -= 0.025; // 7��� ��
	         else if(hitRatio >= 0.04)
	            priceDiscountFactor -= 0.030; // 8��� ��
	         else
	            priceDiscountFactor -= 0.050; // 9��� ��
	      }
	  }
	
	  for (int i = 0, n = rfqBundle.size(); i < n; i++) 
	  {
		  basePrice = SKU_price[rfqBundle.getProductID(i)]; // ����
		  unitProdCycle = SKU_unitProdCycle[rfqBundle.getProductID(i)];
			
		  int dueDate = rfqBundle.getDueDate(i); // RFQ�� ������ �ľ�
		  if ((dueDate - currentDate) >= 6 && (dueDate <= lastBidDueDate) && (totProdCycle <= 1990)) 
		  { 
			  int resPrice = rfqBundle.getReservePricePerUnit(i); // RFQ�� ���Ѱ� �ľ�
			  if (resPrice >= basePrice + selectFilter) // basePrice ���� �̻� �޴´�.
			  {
				  int offeredPrice = (int)(resPrice * priceDiscountFactor);
				  addCustomerOffer(rfqBundle, i, offeredPrice);
				  totProdCycle += (rfqBundle.getQuantity(i) * unitProdCycle); //��ǰ�� unitProdCycle�� totProdCycle�� ����
				  offerQty += rfqBundle.getQuantity(i); //Offer�� ����
			  }
		  }
	  } offerLog[transCnt] = offerQty; // ���� offer�� ���� ����

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
	int orderQty = 0; // ���ſ�û���� ī��Ʈ
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
    orderLog[transCnt] = orderQty; // order�� �ֹ������� ����
	
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