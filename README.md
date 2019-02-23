# **TAC-SCM Agent Implementation**

#### 추구전략
저희 조는 Customer에서 제조회사를 선택할 때 가장 큰 고려요소는“가격”이라는 생각 하에 기본적으로 Customer의 주문성사율에 따라 가격을 조정해 나가는 방안을 채택하기로 전략방향을 정했습니다. 또한 저희는 초기에는 가격우위를 잡기 위해 초기에 가격을 낮추지만, 일정한 가격 이하로는 가격을 낮추지 않도록 설계하였습니다. 여기에서는 이렇게 간략하게 저희의 전략방향을 소개하도록 하겠습니다. 자세한 내용은 첨부문서를 참조해주세요.

**[참고문서]** <https://github.com/DustinYook/Java_TACSCM/blob/master/Final_Report.pdf>

------

## 시뮬레이션 결과
![result](https://github.com/DustinYook/Java_TACSCM/blob/master/game_result.PNG)

-----

## 핵심코드 부분
-----
```java
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
```
