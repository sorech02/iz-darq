import { Component, OnInit } from '@angular/core';
import { Store } from '@ngrx/store';
import { Observable } from 'rxjs';
import { IFacilityDescriptor } from 'src/app/modules/facility/model/facility.model';
import { selectFacilityList, selectReportsNumber, selectCurrentFacility } from '../../store/core.selectors';
import { map } from 'rxjs/operators';

@Component({
  selector: 'app-adf-dashboard',
  templateUrl: './adf-dashboard.component.html',
  styleUrls: ['./adf-dashboard.component.scss']
})
export class AdfDashboardComponent implements OnInit {

  facilities$: Observable<IFacilityDescriptor[]>;
  reportNumbers$: Observable<number>;
  facility$: Observable<string>;

  constructor(private store: Store<any>) {
    this.facilities$ = store.select(selectFacilityList).pipe(
      map((facilities) => {
        return [...facilities].sort((a, b) => {
          if (a.name < b.name) { return -1; }
          if (a.name > b.name) { return 1; }
          return 0;
        });
      })
    );
    this.reportNumbers$ = this.store.select(selectReportsNumber);
    this.facility$ = this.store.select(selectCurrentFacility);
  }

  ngOnInit(): void {
  }

}
