import { Component, OnInit, Inject } from '@angular/core';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { IUser } from '../../../core/model/user.model';

@Component({
  selector: 'app-user-list',
  templateUrl: './user-list.component.html',
  styleUrls: ['./user-list.component.scss']
})
export class UserListComponent implements OnInit {

  users: IUser[];
  pickList: IUser[];
  picked: IUser[];
  selection: string[];

  constructor(
    public dialogRef: MatDialogRef<UserListComponent>,
    @Inject(MAT_DIALOG_DATA) public data: any
  ) {
    this.users = data.users;
    this.selection = data.selection;

    this.pickList = this.users.filter((elm) => {
      return !this.selection.find((s) => s === elm.id);
    });
  }

  dismiss() {
    this.dialogRef.close();
  }

  select() {
    this.dialogRef.close(this.picked.map((d) => d.id));
  }

  ngOnInit(): void {
  }

}
